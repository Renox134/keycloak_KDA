<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>

<@layout.registrationLayout displayMessage=false displayInfo=false displayRequiredFields=false; section>
  <!-- word-typing.ftl -->

  <#if section = "header">
    ${msg("extraWordTitle")!''}

  <#elseif section = "form">
    <div id="kc-form">
      <div id="kc-form-wrapper">
        <form id="kc-form-extra-word"
              class="${properties.kcFormClass!}"
              action="${url.loginAction}"
              method="post">
            <@field.input name="extraWord"
            label=msg("Please type: " + "\"" + extraWord + "\"")
            autofocus=true
            autocomplete="off"/>
          <input type="hidden" name="keystrokeData" id="keystroke-data"/>
          <@buttons.button label="doContinue" />
        </form>
      </div>
    </div>

    <script src="${url.resourcesPath}/js/keystroke-logger.js?v=2.4"></script>
    <script src="${url.resourcesPath}/js/automation-detection.js?v=2.7"></script>

  </#if>
</@layout.registrationLayout>
