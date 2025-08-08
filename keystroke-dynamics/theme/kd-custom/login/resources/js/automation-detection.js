window.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById("kc-form-login");
  if (form) {
    form.addEventListener("submit", handleLoginSubmit);
  }

  const passwordInput = document.querySelector('input[name="password"]');

  if (passwordInput) {
    window.passwordKeystrokeLogger = new KeystrokeLogger(passwordInput);
  } else {
    console.warn('Password input not found for keystroke logging.');
  }
});

window.handleLoginSubmit = async function (event) {
  event.preventDefault();

  const loginButton = document.querySelector('button[name="login"]');
  if (loginButton) loginButton.disabled = true;

  const logger = window.passwordKeystrokeLogger;

  if (!logger) {
    if (loginButton) loginButton.disabled = false;
    return;
  }

  const data = logger.getData();
  const form = document.getElementById('kc-form-login');

  // append keystroke data
  let dataInput = form.querySelector('input[name="keystrokeData"]');
  if(!dataInput){
    dataInput = document.createElement('input');
    dataInput.type = 'hidden';
    dataInput.name = "keystrokeData";
    form.appendChild(dataInput);
  }
  dataInput.value = JSON.stringify(data);
  form.submit();
};
