(() => {
  "use strict";

  const { getToken, login, register, setSession, ApiError } = window.Habitual;

  if (getToken()) {
    window.location.replace("/app.html");
    return;
  }

  const copy = {
    login: {
      eyebrow: "Welcome back",
      title: "Pick up where you left off.",
      subtitle: "Your habits, streaks and calendar are waiting.",
      switch: 'New here? <button type="button" data-mode="register">Create an account</button>',
    },
    register: {
      eyebrow: "Start small",
      title: "Build a rhythm that lasts.",
      subtitle: "Create an account, add a habit, and check in today.",
      switch: 'Already have an account? <button type="button" data-mode="login">Sign in</button>',
    },
  };

  const loginForm = document.getElementById("loginForm");
  const registerForm = document.getElementById("registerForm");
  const errorBox = document.getElementById("authError");
  const eyebrow = document.getElementById("authEyebrow");
  const title = document.getElementById("authTitle");
  const subtitle = document.getElementById("authSubtitle");
  const switchText = document.getElementById("authSwitch");
  const tabs = Array.from(document.querySelectorAll(".auth-tab"));

  function setMode(mode, updateUrl = true) {
    const selected = copy[mode] ? mode : "login";
    tabs.forEach((tab) => {
      const active = tab.dataset.mode === selected;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
    });
    loginForm.hidden = selected !== "login";
    registerForm.hidden = selected !== "register";
    eyebrow.textContent = copy[selected].eyebrow;
    title.textContent = copy[selected].title;
    subtitle.textContent = copy[selected].subtitle;
    switchText.innerHTML = copy[selected].switch;
    hideError();

    if (updateUrl) {
      const base = window.location.pathname + window.location.search;
      window.history.replaceState(null, "", selected === "register" ? `${base}#register` : base);
    }
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function hideError() {
    errorBox.hidden = true;
    errorBox.textContent = "";
  }

  function setLoading(button, loading) {
    button.disabled = loading;
    button.classList.toggle("is-loading", loading);
  }

  function describeError(error) {
    if (error instanceof ApiError && error.fieldErrors) {
      return Object.values(error.fieldErrors).filter(Boolean).join(" ");
    }
    return error.message || "Something went wrong. Please try again.";
  }

  document.addEventListener("click", (event) => {
    const modeButton = event.target.closest("[data-mode]");
    if (modeButton) {
      setMode(modeButton.dataset.mode);
      return;
    }

    const reveal = event.target.closest("[data-reveal]");
    if (reveal) {
      const input = document.getElementById(reveal.dataset.reveal);
      if (!input) return;
      const visible = input.type === "text";
      input.type = visible ? "password" : "text";
      reveal.classList.toggle("is-visible", !visible);
      reveal.setAttribute("aria-label", visible ? "Show password" : "Hide password");
    }
  });

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideError();
    if (!loginForm.reportValidity()) return;

    const button = document.getElementById("loginSubmit");
    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value;
    setLoading(button, true);
    try {
      const auth = await login(email, password);
      setSession(auth);
      window.location.replace("/app.html");
    } catch (error) {
      showError(describeError(error));
      setLoading(button, false);
    }
  });

  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideError();
    if (!registerForm.reportValidity()) return;

    const button = document.getElementById("registerSubmit");
    const payload = {
      displayName: document.getElementById("registerName").value.trim(),
      email: document.getElementById("registerEmail").value.trim(),
      password: document.getElementById("registerPassword").value,
    };
    setLoading(button, true);
    try {
      const auth = await register(payload);
      setSession(auth);
      window.location.replace("/app.html");
    } catch (error) {
      showError(describeError(error));
      setLoading(button, false);
    }
  });

  const params = new URLSearchParams(window.location.search);
  if (params.get("expired") === "1") {
    showError("Your session expired. Please sign in again.");
  }

  setMode(window.location.hash === "#register" ? "register" : "login", false);
})();