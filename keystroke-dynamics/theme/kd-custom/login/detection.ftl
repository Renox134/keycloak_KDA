<#assign detectAutomation = detectAutomation!false>

<#macro detectionScripts>
    <#if detectAutomation?? && detectAutomation>
        <script src="${url.resourcesPath}/js/keystroke-logger.js?v=2.4"></script>
        <script src="${url.resourcesPath}/js/automation-detection.js?v=2.7"></script>
    <#else>
        <script>
            window.handleLoginSubmit = function () {
                const loginButton = document.querySelector('button[name="login"]');
                if (loginButton) {
                    loginButton.disabled = true;
                }
                return true;
            };
        </script>
    </#if>
</#macro>