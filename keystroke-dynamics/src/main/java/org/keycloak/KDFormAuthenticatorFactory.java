package org.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class KDFormAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "kd-form-authenticator";

    private static final KDFormAuthenticator SINGLETON = new KDFormAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // No config for now
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post init
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getDisplayType() {
        return "KD Username Password Form";
    }

    @Override
    public String getReferenceCategory() {
        return "browser";
    }

    @Override
    public boolean isConfigurable() {
        return false; // no custom properties
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Validates username/password with additional keystroke detection.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

}
