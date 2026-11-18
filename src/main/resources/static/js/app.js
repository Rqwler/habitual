(() => {
  "use strict";

  const API = window.Habitual;
  if (!API.getToken()) {
    window.location.replace("/");
    return;
  }

  const COLORS = [
    "#3c8462",
    "#b97027",
    "#5b7fa6",
    "#9a6b9e",
    "#b45e55",
    "#6d8a4a",
    "#8a7857",
    "#4f8b8a",
  ];
  const MONTHS = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
  ];
  const SHORT_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

  const ICONS = {
    grip: `<svg viewBox="0 0 14 18" aria-hidden="true"><circle cx="4" cy="4" r="1.3"/><circle cx="10" cy="4" r="1.3"/><circle cx="4" cy="9" r="1.3"/><circle cx="10" cy="9" r="1.3"/><circle cx="4" cy="14" r="1.3"/><circle cx="10" cy="14" r="1.3"/></svg>`,
    flame: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22c4.4 0 7-2.8 7-6.4 0-2.5-1.3-4.5-3-6.2-.4 1.3-1.1 2.2-2 2.8.1-3.2-1.7-6.2-4.3-7.9.2 3.7-1.2 5.6-2.7 7.4A7.8 7.8 0 0 0 5 15.6C5 19.2 7.6 22 12 22Z"/></svg>`,
    arrowUp: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 15 6-6 6 6"/></svg>`,
    arrowDown: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>`,
    edit: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>`,
    trash: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v5M14 11v5"/></svg>`,
  };

  const els = {
    todayLabel: document.getElementById("todayLabel"),
    greetingDate: document.getElementById("greetingDate"),
    greeting: document.getElementById("greeting"),
    pageSubtitle: document.getElementById("pageSubtitle"),
    avatar: document.getElementById("avatar"),
    userName: document.getElementById("userName"),
    userEmail: document.getElementById("userEmail"),
    logoutBtn: document.getElementById("logoutBtn"),
    refreshBtn: document.getElementById("refreshBtn"),
    addHabitBtn: document.getElementById("addHabitBtn"),

    statToday: document.getElementById("statToday"),
    statTodayDetail: document.getElementById("statTodayDetail"),
    todayRing: document.getElementById("todayRing"),
    todayRingValue: document.getElementById("todayRingValue"),
    statWeek: document.getElementById("statWeek"),
    statWeekDetail: document.getElementById("statWeekDetail"),
    statMonth: document.getElementById("statMonth"),
    statMonthDetail: document.getElementById("statMonthDetail"),
    statBest: document.getElementById("statBest"),
    statBestDetail: document.getElementById("statBestDetail"),

    todayPill: document.getElementById("todayPill"),
    todayList: document.getElementById("todayList"),
    todayEmpty: document.getElementById("todayEmpty"),

    calendarHeading: document.getElementById("calendarHeading"),
    calendarHabitSelect: document.getElementById("calendarHabitSelect"),
    calendarHabitMeta: document.getElementById("calendarHabitMeta"),
    calendarDone: document.getElementById("calendarDone"),
    calendarTarget: document.getElementById("calendarTarget"),
    calendarGrid: document.getElementById("calendarGrid"),
    calendarEmpty: document.getElementById("calendarEmpty"),
    calendarWeekdays: document.querySelector(".calendar-weekdays"),
    calendarLegend: document.querySelector(".calendar-legend"),
    prevMonth: document.getElementById("prevMonth"),
    nextMonth: document.getElementById("nextMonth"),

    habitList: document.getElementById("habitList"),
    habitsEmpty: document.getElementById("habitsEmpty"),

    habitModal: document.getElementById("habitModal"),
    habitForm: document.getElementById("habitForm"),
    habitId: document.getElementById("habitId"),
    habitName: document.getElementById("habitName"),
    habitDescription: document.getElementById("habitDescription"),
    habitCategory: document.getElementById("habitCategory"),
    habitTimesPerWeek: document.getElementById("habitTimesPerWeek"),
    weekdayField: document.getElementById("weekdayField"),
    weeklyField: document.getElementById("weeklyField"),
    habitModalEyebrow: document.getElementById("habitModalEyebrow"),
    habitModalTitle: document.getElementById("habitModalTitle"),
    habitSaveBtn: document.getElementById("habitSaveBtn"),
    habitSaveLabel: document.getElementById("habitSaveLabel"),
    habitError: document.getElementById("habitError"),

    confirmModal: document.getElementById("confirmModal"),
    confirmMessage: document.getElementById("confirmMessage"),
    confirmDeleteBtn: document.getElementById("confirmDeleteBtn"),

    toast: document.getElementById("toast"),
    toastText: document.getElementById("toastText"),
  };

  const state = {
    user: API.getUser() || { displayName: "there", email: "" },
    habits: [],
    stats: null,
    selectedHabitId: null,
    calendar: null,
    calendarCursor: firstOfCurrentMonth(),
    busy: new Set(),
    draggedId: null,
    editingHabitId: null,
    confirmingHabit: null,
    calendarRequest: 0,
  };

  let toastTimer = null;

  // ---------- small utilities ----------
  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => {
      switch (character) {
        case "&":
          return "&amp;";
        case "<":
          return "&lt;";
        case ">":
          return "&gt;";
        case '"':
          return "&quot;";
        default:
          return "&#39;";
      }
    });
  }

  function firstOfCurrentMonth() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  }

  function isoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function parseIsoDate(value) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function todayIso() {
    return isoDate(new Date());
  }

  function firstName(name) {
    if (!name || name === "there") return "there";
    return String(name).trim().split(/\s+/)[0];
  }

  function initials(name) {
    const parts = String(name || "H")
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (!parts.length) return "H";
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
  }

  function habitColor(habit) {
    const source = `${habit.category || ""}${habit.name || ""}`;
    let hash = 0;
    for (let index = 0; index < source.length; index += 1) {
      hash = (hash * 31 + source.charCodeAt(index)) >>> 0;
    }
    return COLORS[hash % COLORS.length];
  }

  function frequencyLabel(habit) {
    if (habit.frequencyType === "DAILY") return "Every day";
    if (habit.frequencyType === "WEEKLY") {
      return `${habit.timesPerWeek}× per week`;
    }
    if (habit.frequencyType === "SPECIFIC_DAYS" && habit.weekdays?.length) {
      return habit.weekdays.map((day) => SHORT_DAYS[day - 1]).join(" · ");
    }
    return "Specific days";
  }

  function streakText(habit) {
    return `${habit.currentStreak} ${habit.streakUnit}`;
  }

  function selectedHabit() {
    return state.habits.find((habit) => habit.id === state.selectedHabitId) || null;
  }

  function setLoading(button, loading) {
    button.disabled = loading;
    button.classList.toggle("is-loading", loading);
  }

  function describeError(error) {
    if (error?.fieldErrors) {
      return Object.values(error.fieldErrors).filter(Boolean).join(" ");
    }
    return error?.message || "Something went wrong. Please try again.";
  }

  function showToast(message, type = "success") {
    window.clearTimeout(toastTimer);
    els.toastText.textContent = message;
    els.toast.classList.toggle("is-error", type === "error");
    els.toast.classList.remove("is-visible");
    els.toast.hidden = false;
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => els.toast.classList.add("is-visible"));
    });
    toastTimer = window.setTimeout(() => {
      els.toast.classList.remove("is-visible");
      window.setTimeout(() => {
        els.toast.hidden = true;
      }, 220);
    }, 3200);
  }

  // ---------- data ----------
  async function loadAll() {
    const today = todayIso();
    const [habits, stats] = await Promise.all([
      API.get(`/habits?date=${today}`),
      API.get(`/stats/dashboard?date=${today}`),
    ]);
    state.habits = Array.isArray(habits) ? habits : [];
    state.stats = stats;
    if (!state.habits.some((habit) => habit.id === state.selectedHabitId)) {
      state.selectedHabitId = state.habits[0]?.id ?? null;
    }
    await loadCalendar();
  }

  async function loadCalendar() {
    if (!state.selectedHabitId) {
      state.calendar = null;
      return;
    }
    const requestId = ++state.calendarRequest;
    const year = state.calendarCursor.getFullYear();
    const month = state.calendarCursor.getMonth() + 1;
    try {
      const calendar = await API.get(
        `/habits/${state.selectedHabitId}/calendar?year=${year}&month=${month}`,
      );
      if (requestId === state.calendarRequest) {
        state.calendar = calendar;
      }
    } catch (error) {
      if (requestId === state.calendarRequest) {
        state.calendar = null;
        showToast(describeError(error), "error");
      }
    }
  }

  async function refreshEverything(showMessage = false) {
    els.refreshBtn.disabled = true;
    els.refreshBtn.classList.add("is-spinning");
    try {
      await loadAll();
      renderAll();
      if (showMessage) showToast("Everything is up to date");
    } catch (error) {
      showToast(describeError(error), "error");
    } finally {
      els.refreshBtn.disabled = false;
      els.refreshBtn.classList.remove("is-spinning");
    }
  }

  // ---------- render ----------
  function renderAll() {
    renderUser();
    renderStats();
    renderToday();
    renderHabits();
    renderCalendar();
  }

  function renderUser() {
    const name = state.user?.displayName || "there";
    els.userName.textContent = name;
    els.userEmail.textContent = state.user?.email || "";
    els.avatar.textContent = initials(name);
    renderDateLabels();
  }

  function renderDateLabels() {
    const now = new Date();
    els.todayLabel.textContent = now.toLocaleDateString(undefined, {
      weekday: "short",
      day: "numeric",
      month: "short",
    });
    els.greetingDate.textContent = now.toLocaleDateString(undefined, {
      weekday: "long",
      day: "numeric",
      month: "long",
    });
    const hour = now.getHours();
    const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    els.greeting.textContent = `${greeting}, ${firstName(state.user?.displayName)}`;
  }

  function renderStats() {
    const stats = state.stats;
    if (!stats) {
      els.statToday.textContent = "—";
      els.statTodayDetail.textContent = "No habits yet";
      els.statWeek.textContent = "—";
      els.statMonth.textContent = "—";
      els.statBest.textContent = "—";
      els.todayPill.textContent = "0 / 0 done";
      return;
    }

    const today = stats.today || { completed: 0, expected: 0, percentage: 0 };
    const week = stats.week || { completed: 0, expected: 0, percentage: 0 };
    const month = stats.month || { completed: 0, expected: 0, percentage: 0 };

    els.statToday.textContent = `${today.completed} / ${today.expected}`;
    els.statTodayDetail.textContent =
      today.expected === 0 ? "Nothing scheduled" : "habits completed today";
    els.todayRing.style.setProperty("--value", String(today.percentage));
    els.todayRingValue.textContent = `${today.percentage}`;
    els.todayPill.textContent = `${today.completed} / ${today.expected} done`;

    els.statWeek.textContent = `${week.percentage}%`;
    els.statWeekDetail.textContent = `${week.completed} of ${week.expected} check-ins`;
    els.statMonth.textContent = `${month.percentage}%`;
    els.statMonthDetail.textContent = `${month.completed} of ${month.expected} check-ins`;

    if (stats.bestCurrentStreak > 0) {
      els.statBest.innerHTML = `${stats.bestCurrentStreak}<small>${escapeHtml(
        stats.bestStreakUnit,
      )}</small>`;
      els.statBestDetail.textContent = `${stats.totalCheckIns} check-in${
        stats.totalCheckIns === 1 ? "" : "s"
      } all time`;
    } else {
      els.statBest.textContent = "—";
      els.statBestDetail.textContent =
        stats.totalCheckIns > 0
          ? `${stats.totalCheckIns} check-in${stats.totalCheckIns === 1 ? "" : "s"} all time`
          : "Start one today";
    }

    const remaining = Math.max(0, today.expected - today.completed);
    if (!state.habits.length) {
      els.pageSubtitle.textContent = "Start with one small habit. Future you says thanks.";
    } else if (today.expected === 0) {
      els.pageSubtitle.textContent = "A rest day. Let the streak breathe.";
    } else if (remaining === 0) {
      els.pageSubtitle.textContent = "Today is complete. Beautiful work.";
    } else {
      els.pageSubtitle.textContent = `${remaining} check-in${
        remaining === 1 ? "" : "s"
      } left today. Keep it gentle.`;
    }
  }

  function renderToday() {
    const habits = state.habits.filter((habit) => habit.scheduledToday);
    if (!habits.length) {
      els.todayList.innerHTML = "";
      els.todayList.hidden = true;
      els.todayEmpty.hidden = false;
      const noHabits = state.habits.length === 0;
      els.todayEmpty.querySelector("strong").textContent = noHabits
        ? "Your day is a blank page"
        : "A quiet day";
      els.todayEmpty.querySelector("p").textContent = noHabits
        ? "Add your first habit and check in with one gentle tap."
        : "Nothing is scheduled for today. Add a daily habit or enjoy the rest day.";
      if (noHabits) {
        els.todayPill.textContent = "0 / 0 done";
      }
      return;
    }

    els.todayList.hidden = false;
    els.todayEmpty.hidden = true;
    els.todayList.innerHTML = habits
      .map((habit) => {
        const color = habitColor(habit);
        const busy = state.busy.has(`${habit.id}:${todayIso()}`);
        const weekly = habit.frequencyType === "WEEKLY";
        return `
          <div class="today-item ${habit.completedToday ? "done" : ""} ${
            busy ? "is-busy" : ""
          }">
            <button class="check" type="button" data-checkin="${habit.id}"
              aria-label="${habit.completedToday ? "Undo check-in for" : "Check in"} ${escapeHtml(
                habit.name,
              )}"
              aria-pressed="${habit.completedToday}">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4 4L19 6.5"/></svg>
            </button>
            <div class="today-copy">
              <div class="today-title">
                <span class="habit-dot" style="--dot:${color}" aria-hidden="true"></span>
                <strong>${escapeHtml(habit.name)}</strong>
                ${
                  habit.category
                    ? `<span class="category-chip">${escapeHtml(habit.category)}</span>`
                    : ""
                }
              </div>
              <div class="today-meta">
                <span>${escapeHtml(frequencyLabel(habit))}</span>
                <span class="streak-inline">
                  ${ICONS.flame}
                  ${habit.currentStreak} ${escapeHtml(habit.streakUnit)}
                </span>
              </div>
            </div>
            <div class="today-side">
              ${
                weekly
                  ? `<span class="week-chip">${habit.weekCompleted} / ${habit.weekTarget} this week</span>`
                  : `<span class="week-chip">${habit.completionRate30d}% · 30d</span>`
              }
            </div>
          </div>
        `;
      })
      .join("");
  }

  function renderHabits() {
    if (!state.habits.length) {
      els.habitList.innerHTML = "";
      els.habitList.hidden = true;
      els.habitsEmpty.hidden = false;
      return;
    }

    els.habitList.hidden = false;
    els.habitsEmpty.hidden = true;
    els.habitList.innerHTML = state.habits
      .map((habit, index) => {
        const color = habitColor(habit);
        return `
          <div class="manage-row" draggable="true" data-habit-id="${habit.id}">
            <button class="grip" type="button" aria-label="Drag ${escapeHtml(
              habit.name,
            )} to reorder" title="Drag to reorder">
              ${ICONS.grip}
            </button>
            <div class="manage-main">
              <div class="manage-title">
                <span class="habit-dot" style="--dot:${color}" aria-hidden="true"></span>
                <strong>${escapeHtml(habit.name)}</strong>
                ${
                  habit.category
                    ? `<span class="category-chip">${escapeHtml(habit.category)}</span>`
                    : ""
                }
              </div>
              <div class="manage-meta">${escapeHtml(frequencyLabel(habit))}</div>
            </div>
            <div class="manage-streak" title="Current streak">
              ${ICONS.flame}
              <span>${habit.currentStreak}</span>
              <small>${escapeHtml(habit.streakUnit)}</small>
            </div>
            <div class="manage-rate" title="Completion over the last 30 days">
              <div class="rate-track">
                <span style="width:${Math.min(100, Math.max(0, habit.completionRate30d))}%"></span>
              </div>
              <small>${habit.completionRate30d}% · 30 days</small>
            </div>
            <div class="manage-actions">
              <button class="row-btn" type="button" data-action="up" data-id="${habit.id}"
                aria-label="Move ${escapeHtml(habit.name)} up" title="Move up" ${
                  index === 0 ? "disabled" : ""
                }>${ICONS.arrowUp}</button>
              <button class="row-btn" type="button" data-action="down" data-id="${habit.id}"
                aria-label="Move ${escapeHtml(habit.name)} down" title="Move down" ${
                  index === state.habits.length - 1 ? "disabled" : ""
                }>${ICONS.arrowDown}</button>
              <button class="row-btn" type="button" data-action="edit" data-id="${habit.id}"
                aria-label="Edit ${escapeHtml(habit.name)}" title="Edit">${ICONS.edit}</button>
              <button class="row-btn danger" type="button" data-action="delete" data-id="${
                habit.id
              }" aria-label="Delete ${escapeHtml(habit.name)}" title="Delete">${ICONS.trash}</button>
            </div>
          </div>
        `;
      })
      .join("");
  }

  function renderCalendar() {
    const habit = selectedHabit();
    const year = state.calendarCursor.getFullYear();
    const month = state.calendarCursor.getMonth();
    els.calendarHeading.textContent = `${MONTHS[month]} ${year}`;

    if (!state.habits.length) {
      els.calendarHabitSelect.innerHTML = '<option value="">No habits yet</option>';
      els.calendarHabitSelect.disabled = true;
      els.calendarHabitSelect.value = "";
      els.calendarHabitMeta.textContent = "Add a habit to begin";
      els.calendarDone.textContent = "0";
      els.calendarTarget.textContent = "0";
      els.calendarGrid.innerHTML = "";
      els.calendarGrid.hidden = true;
      els.calendarWeekdays.hidden = true;
      els.calendarLegend.hidden = true;
      els.calendarEmpty.hidden = false;
      els.prevMonth.disabled = false;
      els.nextMonth.disabled = isCurrentMonth(state.calendarCursor);
      return;
    }

    els.calendarHabitSelect.innerHTML = state.habits
      .map(
        (option) =>
          `<option value="${option.id}">${escapeHtml(option.name)}</option>`,
      )
      .join("");
    els.calendarHabitSelect.disabled = false;
    els.calendarHabitSelect.value = String(habit?.id ?? "");
    els.calendarGrid.hidden = false;
    els.calendarWeekdays.hidden = false;
    els.calendarLegend.hidden = false;
    els.calendarEmpty.hidden = true;
    els.prevMonth.disabled = false;
    els.nextMonth.disabled = isCurrentMonth(state.calendarCursor);

    if (!habit) {
      els.calendarHabitMeta.textContent = "Select a habit";
      els.calendarDone.textContent = "0";
      els.calendarTarget.textContent = "0";
      els.calendarGrid.innerHTML = "";
      return;
    }

    els.calendarHabitMeta.textContent = `${frequencyLabel(habit)}${
      habit.category ? ` · ${habit.category}` : ""
    }`;
    els.calendarDone.textContent = String(state.calendar?.completedCount ?? 0);
    els.calendarTarget.textContent = String(state.calendar?.targetCount ?? 0);

    const firstDay = new Date(year, month, 1);
    const offset = (firstDay.getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const totalCells = Math.ceil((offset + daysInMonth) / 7) * 7;
    const dayMap = new Map(
      (state.calendar?.days || []).map((day) => [day.date, day]),
    );
    const today = todayIso();
    const now = new Date();
    now.setHours(0, 0, 0, 0);

    let cells = "";
    for (let index = 0; index < totalCells; index += 1) {
      if (index < offset || index >= offset + daysInMonth) {
        cells += '<span class="cal-day is-outside" aria-hidden="true"></span>';
        continue;
      }
      const dayNumber = index - offset + 1;
      const date = new Date(year, month, dayNumber);
      const dateKey = isoDate(date);
      const data = dayMap.get(dateKey);
      const scheduled = Boolean(data?.scheduled);
      const completed = Boolean(data?.completed);
      const future = date > now;
      const classes = [
        "cal-day",
        scheduled ? "is-scheduled" : "is-unscheduled",
        completed ? "is-complete" : "",
        dateKey === today ? "is-today" : "",
        future ? "is-future" : "",
      ]
        .filter(Boolean)
        .join(" ");
      const label = `${date.toLocaleDateString(undefined, {
        weekday: "long",
        day: "numeric",
        month: "long",
      })}: ${completed ? "checked in" : scheduled ? "not checked in" : "not scheduled"}`;
      const mark = completed ? "✓" : scheduled && !future ? "·" : "";
      cells += `
        <button class="${classes}" type="button" data-calendar-date="${dateKey}"
          ${!scheduled || future ? "disabled" : ""}
          aria-label="${escapeHtml(label)}"
          title="${escapeHtml(label)}">
          <span>${dayNumber}</span>
          <small>${mark}</small>
        </button>
      `;
    }
    els.calendarGrid.innerHTML = cells;
  }

  function isCurrentMonth(date) {
    const now = new Date();
    return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
  }

  // ---------- check-ins ----------
  async function toggleCheckIn(habitId, date, currentlyCompleted) {
    const key = `${habitId}:${date}`;
    if (state.busy.has(key)) return;
    state.busy.add(key);
    if (date === todayIso()) renderToday();
    try {
      const result = currentlyCompleted
        ? await API.delete(`/habits/${habitId}/check-ins?date=${date}`)
        : await API.post(`/habits/${habitId}/check-ins`, { date });
      await loadAll();
      renderAll();
      showToast(result?.message || (currentlyCompleted ? "Check-in removed" : "Checked in"));
    } catch (error) {
      showToast(describeError(error), "error");
    } finally {
      state.busy.delete(key);
      renderToday();
    }
  }

  // ---------- ordering ----------
  async function persistOrder(ids) {
    try {
      const updated = await API.put("/habits/order", { habitIds: ids });
      if (Array.isArray(updated)) {
        state.habits = updated;
      }
      renderToday();
      renderHabits();
      showToast("Order updated");
    } catch (error) {
      showToast(describeError(error), "error");
      renderHabits();
    }
  }

  function moveHabit(habitId, direction) {
    const ids = state.habits.map((habit) => habit.id);
    const index = ids.indexOf(habitId);
    if (index < 0) return;
    const target = direction === "up" ? index - 1 : index + 1;
    if (target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    persistOrder(ids);
  }

  function handleDrop(targetId) {
    const draggedId = state.draggedId;
    clearDragState();
    if (!draggedId || !targetId || draggedId === targetId) return;
    const ids = state.habits.map((habit) => habit.id);
    const from = ids.indexOf(draggedId);
    const to = ids.indexOf(targetId);
    if (from < 0 || to < 0) return;
    ids.splice(from, 1);
    ids.splice(to, 0, draggedId);
    persistOrder(ids);
  }

  function clearDragState() {
    state.draggedId = null;
    els.habitList
      .querySelectorAll(".is-dragging, .drag-over")
      .forEach((row) => row.classList.remove("is-dragging", "drag-over"));
  }

  // ---------- habit modal ----------
  function updateFrequencyFields() {
    const frequency =
      document.querySelector('input[name="frequencyType"]:checked')?.value || "DAILY";
    els.weekdayField.hidden = frequency !== "SPECIFIC_DAYS";
    els.weeklyField.hidden = frequency !== "WEEKLY";
  }

  function showHabitError(message) {
    els.habitError.textContent = message;
    els.habitError.hidden = false;
  }

  function hideHabitError() {
    els.habitError.hidden = true;
    els.habitError.textContent = "";
  }

  function openHabitModal(habit = null) {
    state.editingHabitId = habit?.id ?? null;
    els.habitForm.reset();
    els.habitId.value = habit?.id ?? "";
    els.habitName.value = habit?.name ?? "";
    els.habitDescription.value = habit?.description ?? "";
    els.habitCategory.value = habit?.category ?? "";
    els.habitTimesPerWeek.value = String(habit?.timesPerWeek ?? 3);
    hideHabitError();

    const frequency = habit?.frequencyType ?? "DAILY";
    document.querySelectorAll('input[name="frequencyType"]').forEach((radio) => {
      radio.checked = radio.value === frequency;
    });
    document.querySelectorAll('input[name="weekday"]').forEach((checkbox) => {
      checkbox.checked = Boolean(habit?.weekdays?.includes(Number(checkbox.value)));
    });

    els.habitModalEyebrow.textContent = habit ? "Fine-tune the practice" : "New practice";
    els.habitModalTitle.textContent = habit ? "Edit habit" : "Add a habit";
    els.habitSaveLabel.textContent = habit ? "Save changes" : "Save habit";
    updateFrequencyFields();

    els.habitModal.hidden = false;
    syncBodyLock();
    window.setTimeout(() => els.habitName.focus(), 40);
  }

  function closeHabitModal() {
    els.habitModal.hidden = true;
    state.editingHabitId = null;
    setLoading(els.habitSaveBtn, false);
    syncBodyLock();
  }

  function openConfirm(habit) {
    state.confirmingHabit = habit;
    els.confirmMessage.textContent = `“${habit.name}” and every check-in attached to it will be permanently deleted. This cannot be undone.`;
    els.confirmModal.hidden = false;
    syncBodyLock();
    window.setTimeout(() => els.confirmDeleteBtn.focus(), 40);
  }

  function closeConfirm() {
    els.confirmModal.hidden = true;
    state.confirmingHabit = null;
    setLoading(els.confirmDeleteBtn, false);
    syncBodyLock();
  }

  function syncBodyLock() {
    const locked = !els.habitModal.hidden || !els.confirmModal.hidden;
    document.body.style.overflow = locked ? "hidden" : "";
  }

  async function saveHabit(event) {
    event.preventDefault();
    hideHabitError();
    if (!els.habitForm.reportValidity()) return;

    const name = els.habitName.value.trim();
    const description = els.habitDescription.value.trim();
    const category = els.habitCategory.value.trim();
    const frequency =
      document.querySelector('input[name="frequencyType"]:checked')?.value || "DAILY";
    const weekdays = Array.from(
      document.querySelectorAll('input[name="weekday"]:checked'),
    ).map((checkbox) => Number(checkbox.value));
    const timesPerWeek = Number(els.habitTimesPerWeek.value);

    if (!name) {
      showHabitError("Give the habit a name.");
      return;
    }
    if (frequency === "SPECIFIC_DAYS" && weekdays.length === 0) {
      showHabitError("Pick at least one weekday for this habit.");
      return;
    }
    if (
      frequency === "WEEKLY" &&
      (!Number.isInteger(timesPerWeek) || timesPerWeek < 1 || timesPerWeek > 7)
    ) {
      showHabitError("Choose a weekly target between 1 and 7.");
      return;
    }

    const payload = {
      name,
      description: description || null,
      category: category || null,
      frequencyType: frequency,
    };
    if (frequency === "WEEKLY") payload.timesPerWeek = timesPerWeek;
    if (frequency === "SPECIFIC_DAYS") payload.weekdays = weekdays;

    const editing = Boolean(state.editingHabitId);
    setLoading(els.habitSaveBtn, true);
    try {
      let saved;
      if (editing) {
        saved = await API.put(`/habits/${state.editingHabitId}`, payload);
      } else {
        saved = await API.post("/habits", payload);
        state.selectedHabitId = saved.id;
        state.calendarCursor = firstOfCurrentMonth();
      }
      closeHabitModal();
      await loadAll();
      renderAll();
      showToast(editing ? "Habit updated" : "Habit created. One small step.");
    } catch (error) {
      showHabitError(describeError(error));
      setLoading(els.habitSaveBtn, false);
    }
  }

  async function deleteHabit() {
    const habit = state.confirmingHabit;
    if (!habit) return;
    setLoading(els.confirmDeleteBtn, true);
    try {
      await API.delete(`/habits/${habit.id}`);
      if (state.selectedHabitId === habit.id) {
        state.selectedHabitId = null;
      }
      closeConfirm();
      await loadAll();
      renderAll();
      showToast("Habit deleted");
    } catch (error) {
      showToast(describeError(error), "error");
      setLoading(els.confirmDeleteBtn, false);
    }
  }

  // ---------- navigation ----------
  async function changeMonth(delta) {
    const next = new Date(
      state.calendarCursor.getFullYear(),
      state.calendarCursor.getMonth() + delta,
      1,
    );
    if (delta > 0 && isCurrentMonth(state.calendarCursor)) return;
    state.calendarCursor = next;
    state.calendar = null;
    await loadCalendar();
    renderCalendar();
  }

  // ---------- event wiring ----------
  function bindEvents() {
    els.addHabitBtn.addEventListener("click", () => openHabitModal());
    document.querySelectorAll("[data-open-habit]").forEach((button) => {
      button.addEventListener("click", () => openHabitModal());
    });

    els.logoutBtn.addEventListener("click", () => {
      API.clearSession();
      window.location.replace("/");
    });

    els.refreshBtn.addEventListener("click", () => refreshEverything(true));

    document.querySelectorAll('input[name="frequencyType"]').forEach((radio) => {
      radio.addEventListener("change", updateFrequencyFields);
    });
    els.habitForm.addEventListener("submit", saveHabit);

    document.querySelectorAll("[data-close-modal]").forEach((button) => {
      button.addEventListener("click", closeHabitModal);
    });
    document.querySelectorAll("[data-close-confirm]").forEach((button) => {
      button.addEventListener("click", closeConfirm);
    });
    els.confirmDeleteBtn.addEventListener("click", deleteHabit);

    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      if (!els.confirmModal.hidden) {
        closeConfirm();
      } else if (!els.habitModal.hidden) {
        closeHabitModal();
      }
    });

    els.todayList.addEventListener("click", (event) => {
      const button = event.target.closest("[data-checkin]");
      if (!button) return;
      const habit = state.habits.find((item) => item.id === Number(button.dataset.checkin));
      if (!habit) return;
      toggleCheckIn(habit.id, todayIso(), habit.completedToday);
    });

    els.calendarGrid.addEventListener("click", (event) => {
      const button = event.target.closest("[data-calendar-date]");
      if (!button || button.disabled) return;
      const habit = selectedHabit();
      if (!habit) return;
      const date = button.dataset.calendarDate;
      const day = state.calendar?.days?.find((item) => item.date === date);
      toggleCheckIn(habit.id, date, Boolean(day?.completed));
    });

    els.calendarHabitSelect.addEventListener("change", async () => {
      state.selectedHabitId = Number(els.calendarHabitSelect.value) || null;
      state.calendar = null;
      await loadCalendar();
      renderCalendar();
    });

    els.prevMonth.addEventListener("click", () => changeMonth(-1));
    els.nextMonth.addEventListener("click", () => changeMonth(1));

    els.habitList.addEventListener("click", (event) => {
      const button = event.target.closest("[data-action]");
      if (!button) return;
      const habit = state.habits.find((item) => item.id === Number(button.dataset.id));
      if (!habit) return;
      if (button.dataset.action === "up") moveHabit(habit.id, "up");
      if (button.dataset.action === "down") moveHabit(habit.id, "down");
      if (button.dataset.action === "edit") openHabitModal(habit);
      if (button.dataset.action === "delete") openConfirm(habit);
    });

    els.habitList.addEventListener("dragstart", (event) => {
      const row = event.target.closest(".manage-row");
      if (!row) return;
      state.draggedId = Number(row.dataset.habitId);
      row.classList.add("is-dragging");
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", row.dataset.habitId);
      }
    });

    els.habitList.addEventListener("dragend", clearDragState);

    els.habitList.addEventListener("dragover", (event) => {
      const row = event.target.closest(".manage-row");
      if (!row || !state.draggedId) return;
      event.preventDefault();
      els.habitList
        .querySelectorAll(".drag-over")
        .forEach((item) => item.classList.remove("drag-over"));
      if (Number(row.dataset.habitId) !== state.draggedId) {
        row.classList.add("drag-over");
      }
    });

    els.habitList.addEventListener("drop", (event) => {
      const row = event.target.closest(".manage-row");
      if (!row) return;
      event.preventDefault();
      handleDrop(Number(row.dataset.habitId));
    });

    els.habitList.addEventListener("dragleave", (event) => {
      if (!els.habitList.contains(event.relatedTarget)) {
        els.habitList
          .querySelectorAll(".drag-over")
          .forEach((item) => item.classList.remove("drag-over"));
      }
    });
  }

  // ---------- boot ----------
  bindEvents();
  renderUser();
  renderAll();

  (async () => {
    try {
      if (!state.user?.displayName || !state.user?.email) {
        state.user = await API.me();
        renderUser();
      }
      await loadAll();
      renderAll();
    } catch (error) {
      showToast(describeError(error), "error");
    }
  })();
})();