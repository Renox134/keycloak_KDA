# 🔐 keycloak_KDA

This project is a Keycloak add-on that detects whether a password was entered manually or via automation.  
It serves as an early prototype for implementing **Keystroke Dynamics-Based Authentication** in Keycloak.

> **Note:** This was developed as part of a Bachelor's thesis and may include some less optimal practices. If you're an experienced Keycloak developer, feel free to improve and adapt.

---

## 💡 How It Works

Keycloak allows for the extension of its functionality via custom **providers**.

This project introduces:
1. A **custom authentication flow** (backend provider)
2. A **custom login theme** (frontend keystroke capture for the flow to work)

These two components work together to analyze keystroke dynamics and detect automation.

---

## How to Build the Provider
Building the provider is the most complicated it gets. Everything afterwards should be straightforward.

### Steps:

1. Clone the Keycloak GitHub repository:
   ```bash
   git clone https://github.com/keycloak/keycloak.git
   cd keycloak
   ```

2. Copy the `keystroke-dynamics` folder (the main module folder of this repo) into the Keycloak project root.

3. Add the module to Keycloak's `pom.xml`:
   ```xml
   <module>keystroke-dynamics</module>
   ```

4. Run the Maven build for the module:
   ```bash
   ./mvnw clean install -pl :keystroke-dynamics -am -DskipTests
   ```

5. After a successful build, you'll find the `.jar` file here:
   ```
   keystroke-dynamics/target/keystroke-dynamics-999.0.0-SNAPSHOT.jar
   ```

---

## 🎨 Where's the Custom Theme?

Inside this repo’s root folder, there’s a `theme/kd-custom/` directory.

This folder contains the custom theme.

> **Note:** Almost everything in this custom theme is an addition. However, one file, namely the `login.ftl` template, is overwritten. Therefore, should the nature of the base `login.ftl` template change as keycloak progresses, it may be better to use the base template as a foundation and then apply changes to it. Fortunately, very little was changed. Currently, there are only three changes:
1. An import was added at the top (```<#import "detection.ftl" as detect>```)
2. In line 16, the "onsubmit" part was coated in an if statement (```<#if !(detectAutomation?? && detectAutomation)>onsubmit="login.disabled = true; return true;"</#if>```)
3. A line containing ```<@detect.detectionScripts />``` was added at the very bottom.


---

## 🧩 How to Integrate the Add-on with a Keycloak Installation

If you haven’t yet set up Keycloak, follow these steps:

1. **Download** a Keycloak release from  
   https://www.keycloak.org/downloads

2. **Unpack** it into a directory of your choice.

3. **Copy the Provider JAR** into:
   ```
   keycloak-[version]/providers/
   ```

4. **Copy the Theme** into:
   ```
   keycloak-[version]/themes/
   ```
   > ⚠️ Only copy the `kd-custom` folder — not the parent `theme` directory.

5. Add a **bootstrap admin user** (if necessary) by executing: 
    - **Linux/macOS**: `bin/kc.sh bootstrap-admin user`
    - **Windows**: `bin\kc.bat bootstrap-admin user`

6. **Build Keycloak** to register the new provider and theme:
   - **Linux/macOS**: `bin/kc.sh build`
   - **Windows**: `bin\kc.bat build`

7. **Start Keycloak in development mode**:
   - **Linux/macOS**: `bin/kc.sh start-dev`
   - **Windows**: `bin\kc.bat start-dev`

---

## ⚙️ How to Activate the New Authentication Flow

1. **Log in** to the Keycloak Admin Console.
2. Go to **Realm Settings → Themes**.
   - Set **Login Theme** to `kd-custom`
   - Click **Save**
3. Navigate to **Authentication → Flows**.
4. Click **Create Flow**
   - Name it `KD Browser Flow` (or something similar)
   - Click **Create**
5. Select your new flow, then click **Add Execution**
   - Choose: `KD Username Password Form` (may not be on the first page)
6. Set its **Requirement** to `Required`
7. Click the **Actions** dropdown → **Bind Flow**
8. The custom flow should now be active and in use.

---

## 🧪 Final Notes

- This is an experimental feature.
- By default, many printers usefull for debugging are enabled through a flag in `KDFormAuhtenticator`. If you don't want that, and before entering production, this flag should be changed to disable the printers.
- You can use browser dev tools to verify that your custom JavaScript files (e.g. `keystroke-logger.js`) are being loaded.
- If you encounter any build or runtime issues, consider clearing your Keycloak build cache and rebuilding (`kc.sh build` again).

---

## 📬 Feedback / Contributions

If you're a Keycloak expert and see ways to improve the setup or code, feel free to open an issue or pull request. I can not promise to react quickly, but generally speaking, this project is educational, and input is welcome!
