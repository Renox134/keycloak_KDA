# 🛠️ Custom Theme Overrides Guide

This guide is only relevant if you're using a **newer Keycloak version** and suspect the custom theme may have compatibility issues.

Specifically, it addresses the case where the built-in `login.ftl` file from the `keycloak.v2` theme has changed significantly.  
This project overrides that file — but only minimally.

---

## 📄 Why Override `login.ftl`?

The custom authentication flow relies on frontend JavaScript being injected into the login page.

This is done via:
- a template import
- a Freemarker tag injection
- a small condition wrapped around a native handler

---

## ✅ Changes Made to the Original `login.ftl`

This project was developed using **Keycloak version 26.3.2**. In that version, the following **three changes** were made to the base `login.ftl` template:

1. **Import added at the top of the file**
   ```ftl
   <#import "detection.ftl" as detect>
2. **Conditional added around the `onsubmit` attribute (line 16)**
    ```ftl
    <#if !(detectAutomation?? && detectAutomation)>onsubmit="login.disabled = true; return true;"</#if>

3. **Injection of JavaScript-rendering Freemarker directive at the very bottom**
    ```ftl
    <@detect.detectionScripts />

## 🔍 How to Get the Latest Base Template
To use the latest official version of login.ftl as your starting point:

1. Clone Keycloak's source code:
    ```
    git clone https://github.com/keycloak/keycloak.git

2. Navigate to:
    ```
    keycloak/themes/src/main/resources/theme/keycloak.v2/login/login.ftl

3. Copy that file into your theme directory:
    ```
    keycloak_KDA/keystroke-dynamics/theme/kd-custom/login/login.ftl

4. Apply the 3 minimal changes listed above.

## Final Tip
Once you’ve updated the template:
- Rebuild your Keycloak installation (kc.sh build)
- Restart in dev mode (kc.sh start-dev)
- Test the login page in the browser dev tools to confirm that:
    - Your custom JavaScript is loaded
    - The login form behavior is still intact