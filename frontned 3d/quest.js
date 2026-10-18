/**
 * One evening in the house.
 *
 * The brass puck on the hall table is how you talk to the house.
 * You are Harsh, just back from a run. Someone else is already home.
 */
(function () {
  "use strict";

  var SCRIPT = [
    {
      id: "walk",
      objective: "Walk to the gold puck.",
      note: "Follow the marker.",
      pin: "Walk here",
    },
    {
      id: "home",
      scene: "arrival",
      objective: "Press 1.",
      note: "Tell the house you are home.",
      pin: "Press 1",
      narration: "She got here first, and she is sick. The house keeps that private. It will not let the room go below 26°.",
    },
    {
      id: "heat",
      scene: "cinema",
      objective: "Press 2.",
      note: "You are hot. Ask for cold air.",
      narration: "The house will not go colder than 26°. She needs it warm. You get a fan, and your watch helps you breathe slowly.",
    },
    {
      id: "night",
      scene: "night",
      objective: "Press 3.",
      note: "Say good night.",
      narration: "The house remembers that you cooled down. It does not remember why she needed the room warm.",
    },
  ];

  var DONE = {
    objective: "That's the demo.",
    note: "The house acts, explains, and keeps her reason private.",
  };

  var index = 0;
  var started = false;
  var introOpen = false;
  var busy = false;
  var nudgeUntil = 0;

  var root;
  var dotsEl;
  var objectiveEl;
  var noteEl;
  var intro;
  var pin;
  var pinLabel;

  function current() {
    return index < SCRIPT.length ? SCRIPT[index] : null;
  }

  function done() {
    return index >= SCRIPT.length;
  }

  function nearPuck() {
    var goals = window.n && window.n.goals;
    var goal = goals && goals[0];
    return !!(goal && goal.reached && goal.reached());
  }

  function puckPoint() {
    var goals = window.n && window.n.goals;
    var goal = goals && goals[0];
    var cam = window.n && window.n.camera;
    if (!goal || !goal.position || !cam || !goal.position.clone || !goal.position.project) return null;
    var point = goal.position.clone();
    point.y += 0.55;
    point.project(cam);
    if (point.z > 1) return null;
    return {
      x: (point.x * 0.5 + 0.5) * window.innerWidth,
      y: (-point.y * 0.5 + 0.5) * window.innerHeight,
    };
  }

  function promoteWalk() {
    var step = current();
    if (step && step.id === "walk" && nearPuck()) {
      index += 1;
      render();
    }
  }

  function allows(scene) {
    if (done()) return true;
    if (!started || introOpen || busy) return false;
    promoteWalk();
    var step = current();
    return !!(step && step.scene === scene);
  }

  function sceneOfChip(node) {
    var text = (node.textContent || "").toLowerCase();
    if (text.indexOf("home") !== -1) return "arrival";
    if (text.indexOf("cinema") !== -1) return "cinema";
    if (text.indexOf("night") !== -1) return "night";
    return null;
  }

  function sceneOfKey(key) {
    if (key === "1") return "arrival";
    if (key === "2") return "cinema";
    if (key === "3") return "night";
    return null;
  }

  function onScene(scene) {
    if (done() || busy || introOpen) return;
    promoteWalk();
    var step = current();
    if (!step || step.scene !== scene) return;
    var told = step.narration;
    index += 1;
    busy = true;
    render(told);
    window.setTimeout(function () {
      busy = false;
      render();
    }, 7000);
  }

  function nudge() {
    if (!started || introOpen || done() || !noteEl) return;
    nudgeUntil = performance.now() + 2200;
    noteEl.textContent = busy ? "Watch the room." : "Not yet. " + (current().objective || "");
  }

  function render(narration) {
    if (!root) return;
    var step = done() ? DONE : current();
    var past = done() ? SCRIPT.length : index;
    dotsEl.textContent = "";
    for (var i = 0; i < SCRIPT.length; i++) {
      var dot = document.createElement("i");
      if (i < past) dot.className = "is-on";
      dotsEl.appendChild(dot);
    }
    if (narration) {
      objectiveEl.textContent = narration;
      objectiveEl.className = "q-line is-voice";
      noteEl.textContent = "";
    } else {
      objectiveEl.textContent = step.objective;
      objectiveEl.className = "q-line";
      noteEl.textContent = nudgeUntil > performance.now() ? noteEl.textContent : step.note || "";
    }
    root.hidden = introOpen;
    placePin();
  }

  function placePin() {
    if (!pin) return;
    var step = current();
    var show = started && !introOpen && !busy && step && step.pin;
    var point = show ? puckPoint() : null;
    if (!point) {
      pin.hidden = true;
      return;
    }
    var left = Math.round(point.x);
    var top = Math.round(point.y);
    pin.hidden = false;
    if (pinLabel.textContent !== step.pin) pinLabel.textContent = step.pin;
    if (pin._x !== left) {
      pin._x = left;
      pin.style.left = left + "px";
    }
    if (pin._y !== top) {
      pin._y = top;
      pin.style.top = top + "px";
    }
  }

  var shadowsReady = false;
  var nextShadow = 0;

  function relaxShadows() {
    var renderer = window.n && window.n.renderer;
    if (!renderer || !renderer.shadowMap) return;
    var now = performance.now();
    if (!shadowsReady) {
      renderer.shadowMap.autoUpdate = false;
      renderer.shadowMap.needsUpdate = true;
      shadowsReady = true;
      nextShadow = now + 400;
      return;
    }
    if (now < nextShadow) return;
    renderer.shadowMap.needsUpdate = true;
    nextShadow = now + 400;
  }

  function lockPointer() {
    var canvas = document.querySelector("canvas");
    if (!canvas || !canvas.requestPointerLock || document.pointerLockElement) return;
    try { canvas.requestPointerLock(); } catch (err) {}
  }

  function closeIntro() {
    introOpen = false;
    if (intro) intro.hidden = true;
    render();
  }

  function block(event) {
    event.preventDefault();
    event.stopImmediatePropagation();
    nudge();
  }

  function consider(scene, event) {
    if (!scene || !started) return;
    if (!allows(scene)) {
      block(event);
      return;
    }
    onScene(scene);
  }

  function onKey(event) {
    if (event.repeat || !started) return;
    var key = event.key.toLowerCase();
    if (key !== "escape") lockPointer();
    if (introOpen && (key === "enter" || key === " ")) {
      event.preventDefault();
      event.stopImmediatePropagation();
      closeIntro();
      return;
    }
    var scene = sceneOfKey(key);
    if (scene) {
      consider(scene, event);
      return;
    }
    if (key !== "e" || introOpen) return;
    var chips = document.querySelector(".ha-chips.on");
    if (!chips) return;
    promoteWalk();
    var step = current();
    if (!step) return;
    if (step.scene === "arrival" || step.scene === "cinema") consider(step.scene, event);
  }

  function onClick(event) {
    lockPointer();
    if (event.target && event.target.closest && event.target.closest(".q-intro")) return;
    var chip = event.target && event.target.closest && event.target.closest(".ha-chip");
    if (!chip) return;
    consider(sceneOfChip(chip), event);
  }

  function mount() {
    var style = document.createElement("style");
    style.textContent = [
      ".ha-ghost{display:none!important}",
      ".q-card{position:fixed;z-index:40;top:22px;left:50%;transform:translateX(-50%);",
      "width:min(36rem,calc(100vw - 32px));padding:14px 18px 16px;pointer-events:none;",
      "background:rgba(8,10,16,.9);border:1px solid rgba(224,187,96,.38);border-radius:16px;",
      "box-shadow:0 10px 40px rgba(0,0,0,.35);",
      "font-family:Inter,ui-sans-serif,system-ui,sans-serif;color:#f3ead8}",
      ".q-kicker{display:flex;align-items:center;justify-content:space-between;gap:12px;",
      "font-size:11px;letter-spacing:.16em;text-transform:uppercase;color:#e0bb60}",
      ".q-dots{display:flex;gap:6px}",
      ".q-dots i{width:7px;height:7px;border-radius:99px;background:rgba(224,187,96,.28)}",
      ".q-dots i.is-on{background:#e0bb60}",
      ".q-line{margin:8px 0 0;font-family:'Cormorant Garamond',Georgia,serif;",
      "font-size:clamp(20px,2.4vw,28px);line-height:1.25;font-weight:500}",
      ".q-line.is-voice{font-style:italic;font-size:clamp(18px,2.1vw,24px)}",
      ".q-note{margin:6px 0 0;font-size:14px;color:rgba(243,234,216,.78)}",
      ".q-pin{position:fixed;z-index:35;transform:translate(-50%,-100%);pointer-events:none;",
      "display:flex;flex-direction:column;align-items:center;gap:6px}",
      ".q-pin[hidden]{display:none}",
      ".q-pin b{padding:4px 10px;border-radius:999px;background:rgba(8,10,16,.8);",
      "border:1px solid rgba(224,187,96,.7);color:#f6e7c3;font-size:13px;font-weight:600;",
      "font-family:Inter,ui-sans-serif,system-ui,sans-serif;letter-spacing:.02em}",
      ".q-pin i{width:14px;height:14px;border-radius:50%;background:#e0bb60;",
      "animation:q-pulse 1.6s ease-out infinite}",
      "@keyframes q-pulse{0%{transform:scale(1);opacity:1}70%{transform:scale(1.45);opacity:.45}100%{transform:scale(1);opacity:1}}",
      ".q-intro{position:fixed;inset:0;z-index:60;display:flex;align-items:center;justify-content:center;",
      "padding:24px;background:rgba(5,6,12,.55);font-family:Inter,ui-sans-serif,system-ui,sans-serif}",
      ".q-intro[hidden]{display:none}",
      ".q-intro-card{width:min(34rem,100%);padding:28px 28px 22px;border-radius:18px;",
      "background:#141820;border:1px solid rgba(224,187,96,.45);color:#f3ead8;",
      "box-shadow:0 18px 60px rgba(0,0,0,.45)}",
      ".q-intro h2{margin:8px 0 12px;font-family:'Cormorant Garamond',Georgia,serif;",
      "font-size:clamp(28px,4vw,40px);font-weight:500;letter-spacing:-.02em}",
      ".q-intro p{margin:0 0 10px;font-size:16px;line-height:1.45;color:rgba(243,234,216,.88)}",
      ".q-intro ol{margin:8px 0 18px;padding-left:1.2em;font-size:16px;line-height:1.55}",
      ".q-intro button{min-height:44px;padding:0 18px;border-radius:999px;border:1.5px solid #e0bb60;",
      "background:#e0bb60;color:#1a140c;font-weight:700;font-size:15px;cursor:pointer}",
    ].join("");
    document.head.appendChild(style);

    root = document.createElement("aside");
    root.className = "q-card";
    root.hidden = true;
    root.setAttribute("aria-live", "polite");
    var kicker = document.createElement("div");
    kicker.className = "q-kicker";
    var label = document.createElement("span");
    label.textContent = "One evening";
    dotsEl = document.createElement("span");
    dotsEl.className = "q-dots";
    kicker.appendChild(label);
    kicker.appendChild(dotsEl);
    objectiveEl = document.createElement("p");
    objectiveEl.className = "q-line";
    noteEl = document.createElement("p");
    noteEl.className = "q-note";
    root.appendChild(kicker);
    root.appendChild(objectiveEl);
    root.appendChild(noteEl);
    document.body.appendChild(root);

    pin = document.createElement("div");
    pin.className = "q-pin";
    pin.hidden = true;
    pinLabel = document.createElement("b");
    var dot = document.createElement("i");
    pin.appendChild(pinLabel);
    pin.appendChild(dot);
    document.body.appendChild(pin);

    intro = document.createElement("div");
    intro.className = "q-intro";
    intro.hidden = true;
    intro.innerHTML =
      '<div class="q-intro-card" role="dialog" aria-labelledby="q-intro-title">' +
      '<div class="q-kicker"><span>HomeOS</span></div>' +
      '<h2 id="q-intro-title">This is a house that listens.</h2>' +
      "<p>You are Harsh. You just got back from a run.</p>" +
      "<p>Someone else is already home, and she feels sick. The house knows. It will not tell you why.</p>" +
      "<p>The gold puck on the hall table is how you talk to the house.</p>" +
      "<ol><li>Walk to the puck</li><li>Press 1 — you are home</li><li>Press 2 — ask for cold air</li><li>Press 3 — say good night</li></ol>" +
      '<button type="button">Press Enter</button></div>';
    intro.querySelector("button").addEventListener("click", closeIntro);
    intro.addEventListener("click", function (event) {
      if (event.target === intro) closeIntro();
    });
    document.body.appendChild(intro);
  }

  function frame() {
    relaxShadows();
    if (root && !root.isConnected) document.body.appendChild(root);
    if (pin && !pin.isConnected) document.body.appendChild(pin);
    if (intro && !intro.isConnected) document.body.appendChild(intro);
    if (!started) {
      if (document.querySelector(".ha-title.out")) {
        started = true;
        introOpen = true;
        intro.hidden = false;
        window.addEventListener("keydown", onKey, true);
        document.addEventListener("click", onClick, true);
      }
    } else {
      if (nudgeUntil && performance.now() > nudgeUntil) {
        nudgeUntil = 0;
        if (!busy && !introOpen) render();
      }
      if (!busy && !introOpen) promoteWalk();
      placePin();
    }
    window.setTimeout(frame, 200);
  }

  function boot() {
    var arm = function () {
      if (!document.querySelector(".ha-root") || root) return;
      mount();
      frame();
      observer.disconnect();
    };
    var observer = new MutationObserver(arm);
    observer.observe(document.documentElement, { childList: true, subtree: true });
    arm();
    window.HomeOSQuest = {
      allows: allows,
      closeIntro: closeIntro,
      step: function () {
        return introOpen ? "intro" : done() ? "done" : current().id;
      },
    };
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
