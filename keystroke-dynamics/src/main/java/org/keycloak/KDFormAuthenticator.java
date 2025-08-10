package org.keycloak;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.services.managers.AuthenticationManager;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that creates a form that can dynamically react to whether or not a user
 * uses automation during login so that keystroke dynamics data are captured
 * from another typing challenge if the password wasn't entered by a human. Whether automation was used or not
 * can either be determined with a heuristic approach that leverages the below described thresholds
 * or with a machine learning based classifier. The weights and bias for the classifier were calculated with
 * a logistic regression model.
 * Constants:
 * @value passwordMinLength: The min length that we would expect a password to have. If the keystroke list recorded during
 * login contains less than passwordMinLength elements, it is automatically assumed that automation was used.
 *
 * @value medianDownDownThreshold: If the median_down_down_time (time between two keydown events) is at or below this
 * threshold (in milliseconds), we assume that automation was used.
 *
 * @value medianDistanceThreshold: The distances here are the distances between the down_down_times of the login.
 * Example: say we have [58, 59, 95.5, 65] as down_down_times, then the distances would be [0.5, 1, 4.5] and the median
 * distance would be 1 (we assume sorted lists).
 * If this median distance is at or below the threshold, we assume automation.
 *
 * @value medianDwellTimeThreshold: If the median_dwell_time (time of how long a key is pressed) is at or below this
 * threshold (in milliseconds), we assume that automation was used.
 *
 * @value weights: The weights for the machine learning classifier, generated with a logistic regression approach.
 *
 * @value bias: The bias for the machine learning classifier, generated with a logistic regression approach.
 *
 * @value decisionThreshold: Threshold for the ML classifier describing the probability necessary for
 * automation to be assumed. It is slightly below 0.5 to reduce the chances of false negatives.
 *
 * @value production: A flag indicating whether the module is used in production.
 * When disabled, printers are activated that can help debugging.
 */
public class KDFormAuthenticator implements Authenticator {

    String DEFAULT_TYPING_CHALLENGE = "Ghost town#34";
    private int passwordMinLength = 4;
    private final int medianDownDownThreshold = 70;
    private final float medianDistanceThreshold = 1.1f;
    private final int medianDwellTimeThreshold = 40;
    private final double [] weights = {-0.1367, -0.0271, -0.10671};
    private final double bias = 19.17261;
    private final double decisionThreshold = 0.45;
    private final boolean production = false;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();

        String loginHint = context.getAuthenticationSession().getClientNote("login_hint");
        String rememberMeUsername = AuthenticationManager.getRememberMeUsername(context.getSession());

        PasswordPolicy policy = context.getRealm().getPasswordPolicy();
        if (policy != null) {
            if (! production) System.out.println("Raw policy string: " + policy);
            Integer minLength = policy.getPolicyConfig("length");
            if (minLength != null) {
                passwordMinLength = minLength;
            }
        }

        if (loginHint != null) {
            formData.add(AuthenticationManager.FORM_USERNAME, loginHint);
        } else if (rememberMeUsername != null) {
            formData.add(AuthenticationManager.FORM_USERNAME, rememberMeUsername);
            formData.add("rememberMe", "on");
        }

        context.form().setAttribute("detectAutomation", true);

        Response challenge = context.form().setFormData(formData).createLoginUsernamePassword();
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("cancel")) {
            context.cancelLogin();
            return;
        }

        // If this is the first login attempt
        if (context.getAuthenticationSession().getAuthNote("username") == null) {
            String username = formData.getFirst(AuthenticationManager.FORM_USERNAME);
            String password = formData.getFirst(PasswordCredentialModel.TYPE);
            String keystrokes = formData.getFirst("keystrokeData");

            if (! production) System.out.println("Info: Received data:\n" + keystrokes);
            // check if credentials were entered
            if (username == null || password == null) {
                failureChallenge(context);
                return;
            }

            RealmModel realm = context.getRealm();
            KeycloakSession session = context.getSession();
            UserModel user = session.users().getUserByUsername(realm, username);

            // check if user exists
            if (user == null) {
                failureChallenge(context);
                return;
            }

            // check if credentials match
            UserCredentialManager credentialManager = new UserCredentialManager(session, realm, user);
            CredentialInput credential = new UserCredentialModel(username, PasswordCredentialModel.TYPE, password);
            if (!credentialManager.isValid(List.of(credential))) {
                failureChallenge(context);
                return;
            }
            // check for automation
            boolean isAutomated = isTypeOneAutomation(keystrokes) || isAutomatedML(getVector(keystrokes));

            context.setUser(user);
            context.getAuthenticationSession().setAuthNote("username", username);

            if (isAutomated) {
                if (! production) System.out.println("Info: Automation detected");
                context.form().setAttribute("extraWord", getWord(context));
                Response extraWordPage = context.form()
                        .setAttribute("section", "form")
                        .createForm("word-typing.ftl");
                context.challenge(extraWordPage);
            } else {
                if (evaluateKeystrokes(context, keystrokes)) context.success();
                else {
                    failureChallenge(context);
                }
            }
        }
        else {
            // This is the word challenge step:
            String keystrokes = formData.getFirst("keystrokeData");
            if (! production) System.out.println("Info: Received extra word data\n" + keystrokes);

            if (evaluateKeystrokes(context, keystrokes)) context.success();
            else {
                failureChallenge(context);
            }
        }
    }

    public void failureChallenge(AuthenticationFlowContext context) {
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setAttribute("detectAutomation", true)
                        .setError("Invalid username or password.").createLoginUsernamePassword());
    }

    /**
     * Checks whether the given keystrokes indicate type one automation, meaning automation where
     * the password was pasted or inserted in some way, without generating synthetic keystrokes in the process.
     * @param keystrokes The keystrokes to analyze.
     * @return Returns true in three cases:
     * Case one: an insert was found where more than one character was entered at once
     * Case two: There are exactly 0 keydown and keyup events (which indicates phone usage) and less than
     * passwordMinLength inserts.
     * Case three: The number of keydown or keyup events is below the min password length.
     */
    private boolean isTypeOneAutomation(String keystrokes){
        System.out.println("Received keystrokes: " + keystrokes);

        Pattern typeDownPattern = Pattern.compile("\"type\"\\s*:\\s*\"down\"");
        Pattern typeUpPattern = Pattern.compile("\"type\"\\s*:\\s*\"up\"");
        Pattern typeInsertPattern = Pattern.compile("\"type\"\\s*:\\s*\"insert\"");
        Pattern insertLengthPattern = Pattern.compile("\"len\"\\s*:\\s*(\\d+)");

        int downCount = 0;
        int upCount = 0;
        int insertCount = 0;

        Matcher m;

        // check for inserts with more than one character
        m = insertLengthPattern.matcher(keystrokes);
        while (m.find()) {
            int currentLen = Integer.parseInt(m.group(1));
            if (currentLen > 1) return true;
        }

        // count ups and downs
        m = typeUpPattern.matcher(keystrokes);
        while (m.find()) upCount++;

        m = typeDownPattern.matcher(keystrokes);
        while (m.find()) downCount++;

        m = typeInsertPattern.matcher(keystrokes);
        while (m.find()) insertCount++;

        // case for phone or tablet (device without physical keyboard, where only insert events can occur)
        if(upCount == 0 && downCount == 0) return insertCount < this.passwordMinLength;

        return upCount < this.passwordMinLength || downCount < this.passwordMinLength;
    }

    /**
     * Calculates the vector containing the classification features, given the captured keystrokes.
     * @param keystrokes The captured keystrokes.
     * @return A vector containing the median down down time, median down down difference, and the median dwell time.
     */
    private List<Double> getVector(String keystrokes) {
        List<Double> vector = new ArrayList<>();
        Pattern downDownPattern = Pattern.compile("\"down_down\"\\s*:\\s*(\\d+(\\.\\d+)?)");
        Pattern dwellTimePattern = Pattern.compile("\"dwellTime\"\\s*:\\s*(\\d+(\\.\\d+)?)");

        List<Double> downDowns = new ArrayList<>();
        List<Double> dwellTimes = new ArrayList<>();

        Matcher m;

        m = downDownPattern.matcher(keystrokes);
        while (m.find()) downDowns.add(Double.parseDouble(m.group(1)));

        m = dwellTimePattern.matcher(keystrokes);
        while (m.find()) dwellTimes.add(Double.parseDouble(m.group(1)));

        downDowns.sort(Double::compareTo);
        dwellTimes.sort(Double::compareTo);

        List<Double> downDownDistances = new ArrayList<>();
        for (int i = 0; i < downDowns.size() - 1; i++) {
            double diff = Math.round((downDowns.get(i + 1) - downDowns.get(i)) * 100.0) / 100.0;
            downDownDistances.add(diff);
        }
        downDownDistances.sort(Double::compareTo);
        vector.add(median(downDowns));
        vector.add(median(downDownDistances));
        vector.add(median(dwellTimes));

        // null values can only appear if we had empty lists, so we should assume automation
        for (int i = 0; i < vector.size() - 1; i++) {
            if (vector.get(i) == null){
                if (! production) System.out.println("Vector at index " + i + " is null.");
                vector.clear();
                return vector;
            }
        }
        return vector;
    }

    /**
     * Calculates the median value of the given list. The value is rounded to two digits.
     * @param list of values to find the median of.
     * @return The median value.
     */
    private static Double median(List<Double> list) {
        if (list == null || list.isEmpty()) return null;
        int mid = list.size() / 2;
        if (list.size() % 2 == 0) {
            return Math.round(((list.get(mid) + list.get(mid - 1)) / 2.0) * 100.0) / 100.0;
        } else {
            return Math.round(list.get(mid) * 100.0) / 100.0;
        }
    }

    /**
     * Calculates whether the given vector indicates automated or human credential entry.
     * To do the calculation, a logistic regression-based approach is used.
     * @param vector that contains the features to analyze.
     * @return True if the probability for automation was assessed to be above 45%.
     */
    private boolean isAutomatedML(List<Double> vector) {
        if(vector.isEmpty()) return true;
        else if (! production) System.out.println("Info: Vector: " + vector.toString());
        double z = bias;

        for (int i = 0; i < vector.size(); i++) {
            z += vector.get(i) * weights[i];
        }
        double probability = 1.0 / (1.0 + Math.exp(-z));
        if (! production) System.out.println("Info: Probability: " + probability);
        return probability > decisionThreshold;
    }

    /**
     * Calculates whether the given vector indicates automated or human credential entry.
     * @param vector that includes the features to analyze.
     * @return True if either the vector is too short (for some reason) or if one of the
     * thresholds was passed. Each threshold is explained in the class comment.
     */
    private boolean isAutomatedHeuristic(List<Double> vector) {
        if(vector.size() < 3) return true; // return early if vector is too small
        else if (! production) System.out.println("Info: Vector: " + vector.toString());
        return (vector.get(0) <= medianDownDownThreshold && vector.get(1) >= medianDistanceThreshold &&
                vector.get(2) <= medianDwellTimeThreshold);
    }

    /**
     * Evaluates whether the provided keystrokes match the users past profile.
     * TODO: Implement
     * @param context The context of the authentication.
     * @param keystrokes The keystrokes to analyze.
     * @return True if it is assessed that the keystrokes belong to the user trying to authenticate.
     */
    public boolean evaluateKeystrokes(AuthenticationFlowContext context, String keystrokes) {
        return true;
    }

    /**
     * Calculates the index of the additional typing challenge, should it be necessary. The index is calculated
     * based on the hash of the username.
     * @param username The username of the user trying to authenticate.
     * @param limit The amount of entries within the file containing tha typing challenges.
     * @return The index of the challenge to use.
     * @throws Exception should the SHA-256 digest not be usable (for some reason).
     */
    private int computeWordIndex(String username, int limit) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(username.getBytes(StandardCharsets.UTF_8));
        BigInteger hashInt = new BigInteger(1, hash);
        return hashInt.mod(BigInteger.valueOf(limit)).intValue();
    }

    /**
     * Gets the word that is used as additional typing challenge, should automation have been used.
     * @param context The authentication context.
     * @return the word to use as additional typing challenge.
     */
    public String getWord(AuthenticationFlowContext context) {
        String username = context.getAuthenticationSession().getAuthNote("username");
        try (InputStream inputStream = KDFormAuthenticator.class.getClassLoader().getResourceAsStream("wordlist_7454.txt")) {

            if (inputStream == null) {
                throw new FileNotFoundException("wordlist_7454.txt not found in resources");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            List<String> lines = br.lines().toList();
            return lines.get(computeWordIndex(username, lines.size()));

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return DEFAULT_TYPING_CHALLENGE;
        }

    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
