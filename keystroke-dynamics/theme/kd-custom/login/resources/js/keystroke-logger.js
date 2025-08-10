class KeystrokeLogger {
  constructor(inputElement) {
    this.inputElement = inputElement;
    this.keystrokes = [];
    this.typingStart = null;
    this.lastKeyDownTime = null;
    this.activeKeys = new Set();
    this.keyDownTimestamps = {};
    this.previousValue = "";

    this.init();
  }

  init() {
    if (!this.inputElement) {
      console.error('Input element not found for KeystrokeLogger.');
      return;
    }

    this.inputElement.addEventListener('keydown', (e) => this.handleKeyDown(e));
    this.inputElement.addEventListener('keyup', (e) => this.handleKeyUp(e));
    this.inputElement.addEventListener('input', () => this.handleInput());
  }

  handleInput() {
    const currentValue = this.inputElement.value;
    const prev = this.previousValue;

    // If input is cleared, assume a reset and wipe all logging
    if (prev.length > 0 && currentValue.length === 0) {
      this.reset(); // Clear all internal tracking
    }
    this.previousValue = currentValue;
    const now = performance.now();

    if (!this.typingStart) {
      this.typingStart = now;
    }

    const change = this.diffStrings(prev, currentValue);

    if (change) {
      this.keystrokes.push({
        len: change.len,
        type: change.type,
        timestamp: this.round(now - this.typingStart)
      });
    }
  }

  diffStrings(oldStr, newStr) {
    if (newStr.length > oldStr.length) {
      return {
        len: newStr.length - oldStr.length,
        type: 'insert'
      };
    } else {
      return null;
    }
  }

  round(value) {
    return value !== null ? +value.toFixed(4) : null;
  }

  handleKeyDown(event) {
    const now = performance.now();

    if (!this.typingStart) {
      this.typingStart = now;
    }

    const key = event.key;

    if (!this.activeKeys.has(key)) {
      this.activeKeys.add(key);
      this.keyDownTimestamps[key] = now;

      const down_down = this.lastKeyDownTime !== null && this.keystrokes.length > 0 ? now - this.lastKeyDownTime : null;

      if (key.length === 1) {
        this.keystrokes.push({
          type: 'down',
          timestamp: this.round(now - this.typingStart),
          down_down: this.round(down_down)
        });
      }
      this.lastKeyDownTime = now;
    }
  }

  handleKeyUp(event) {
    const now = performance.now();
    const key = event.key;

    const downTime = this.keyDownTimestamps[key];
    const dwellTime = downTime !== undefined ? now - downTime : null;

    if (key.length === 1) {
      this.keystrokes.push({
        type: 'up',
        timestamp: this.round(now - this.typingStart),
        dwellTime: this.round(dwellTime),
      });
    }

    this.activeKeys.delete(key);
    delete this.keyDownTimestamps[key];
  }

  getData() {
    const lastKeystroke = this.keystrokes.length > 0
        ? this.keystrokes[this.keystrokes.length - 1]
        : null;

    let totalTime = 0;
    if (this.typingStart && lastKeystroke){
      let first_down = -0.01;
      for (let j = 0; j < this.keystrokes.length; j++){
        if(this.keystrokes[j]["type"] === 'down' || this.keystrokes[j]["type"] === 'insert'){
          first_down = this.keystrokes[j]["timestamp"];
          break;
        }
      }
      totalTime = this.round(lastKeystroke.timestamp - first_down)
    }

    return {
      keystrokes: this.keystrokes,
      totalTime: totalTime
    };
  }

  reset() {
    this.keystrokes = [];
    this.typingStart = null;
    this.keyDownTimestamps = {};
    this.lastKeyDownTime = null;
    this.activeKeys.clear();
    this.previousValue = "";
  }
}
