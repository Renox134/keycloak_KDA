window.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById("kc-form-extra-word");
  const input = document.querySelector('input[name="extraWord"]');

  if (!input) {
    console.warn('Extra word input not found.');
    return;
  }

  const logger = new KeystrokeLogger(input);

  form.addEventListener("submit", () => {
    const keystrokeInput = document.getElementById("keystroke-data");
    keystrokeInput.value = JSON.stringify(logger.getData());
  });
});
