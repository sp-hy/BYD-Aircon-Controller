/* BYD Trip Stats — marketing site interactions
   No dependencies, no build step. */

(function () {
  "use strict";

  /* ---- Mobile nav toggle ---- */
  const toggle = document.getElementById("navToggle");
  const links = document.getElementById("navLinks");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      const open = links.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", String(open));
    });
    // Close the menu after tapping a link
    links.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", function () {
        links.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  /* ---- Add a solid background to the nav once scrolled ---- */
  const nav = document.getElementById("nav");
  if (nav) {
    const onScroll = function () {
      nav.classList.toggle("is-scrolled", window.scrollY > 12);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* ---- Screenshot placeholders ----
     Each <figure class="shot"> wraps an <img class="js-shot">. Until the real
     image exists at its src, show a labelled empty frame. As soon as the user
     drops a file at that path it loads and the placeholder disappears — no HTML
     edits needed. */
  document.querySelectorAll("img.js-shot").forEach(function (img) {
    const figure = img.closest(".shot");
    if (!figure) return;

    const markEmpty = function () { figure.classList.add("is-empty"); };
    const markFilled = function () { figure.classList.remove("is-empty"); };

    if (img.complete) {
      // naturalWidth === 0 means it failed / is a placeholder path
      if (img.naturalWidth === 0) markEmpty(); else markFilled();
    }
    img.addEventListener("error", markEmpty);
    img.addEventListener("load", function () {
      if (img.naturalWidth > 0) markFilled();
    });
  });

  /* ---- Lightbox: click any loaded screenshot to view it full-size ----
     Placeholders (figures marked .is-empty) are skipped. The navigable set is
     rebuilt on each open, so it always reflects whichever screenshots exist. */
  (function () {
    const overlay = document.createElement("div");
    overlay.className = "lightbox";
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-label", "Screenshot viewer");
    overlay.innerHTML =
      '<button class="lightbox__btn lightbox__close" aria-label="Close (Esc)">' +
        '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M18.3 5.7 12 12l6.3 6.3-1.4 1.4L10.6 13.4 4.3 19.7l-1.4-1.4L9.2 12 2.9 5.7l1.4-1.4 6.3 6.3 6.3-6.3z"/></svg>' +
      '</button>' +
      '<button class="lightbox__btn lightbox__nav lightbox__prev" aria-label="Previous (left arrow)">' +
        '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z"/></svg>' +
      '</button>' +
      '<button class="lightbox__btn lightbox__nav lightbox__next" aria-label="Next (right arrow)">' +
        '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M8.6 7.4 10 6l6 6-6 6-1.4-1.4L13.2 12z"/></svg>' +
      '</button>' +
      '<figure class="lightbox__figure">' +
        '<img class="lightbox__img" alt="" />' +
        '<figcaption class="lightbox__cap"></figcaption>' +
      '</figure>';
    document.body.appendChild(overlay);

    const lbImg = overlay.querySelector(".lightbox__img");
    const lbCap = overlay.querySelector(".lightbox__cap");
    const btnClose = overlay.querySelector(".lightbox__close");
    const btnPrev = overlay.querySelector(".lightbox__prev");
    const btnNext = overlay.querySelector(".lightbox__next");

    let shots = [];      // currently navigable images
    let index = 0;
    let lastFocus = null;

    function collectShots() {
      return Array.prototype.filter.call(
        document.querySelectorAll(".shot:not(.is-empty) img"),
        function (img) { return img.naturalWidth > 0; }
      );
    }

    function captionFor(img) {
      const fig = img.closest(".shot");
      const cap = fig && fig.querySelector("figcaption");
      return (cap && cap.textContent.trim()) || img.alt || "";
    }

    function show(i) {
      index = (i + shots.length) % shots.length;
      const img = shots[index];
      lbImg.src = img.currentSrc || img.src;
      lbImg.alt = img.alt || "";
      const label = captionFor(img);
      lbCap.innerHTML = label
        ? label + (shots.length > 1 ? '<span>' + (index + 1) + ' / ' + shots.length + '</span>' : '')
        : (shots.length > 1 ? '<span>' + (index + 1) + ' / ' + shots.length + '</span>' : '');
      overlay.setAttribute("data-single", String(shots.length <= 1));
    }

    function open(img) {
      shots = collectShots();
      const start = shots.indexOf(img);
      if (start === -1) return;
      lastFocus = document.activeElement;
      show(start);
      overlay.classList.add("is-open");
      document.body.style.overflow = "hidden";
      btnClose.focus();
    }

    function close() {
      overlay.classList.remove("is-open");
      document.body.style.overflow = "";
      lbImg.removeAttribute("src");
      if (lastFocus && lastFocus.focus) lastFocus.focus();
    }

    // Open on click of any loaded screenshot
    document.querySelectorAll(".shot").forEach(function (fig) {
      fig.addEventListener("click", function () {
        const img = fig.querySelector("img");
        if (img && !fig.classList.contains("is-empty") && img.naturalWidth > 0) open(img);
      });
    });

    btnClose.addEventListener("click", close);
    btnPrev.addEventListener("click", function () { show(index - 1); });
    btnNext.addEventListener("click", function () { show(index + 1); });
    // Click the dimmed backdrop (not the image/buttons) to close
    overlay.addEventListener("click", function (e) {
      if (e.target === overlay || e.target.classList.contains("lightbox__figure")) close();
    });
    document.addEventListener("keydown", function (e) {
      if (!overlay.classList.contains("is-open")) return;
      if (e.key === "Escape") close();
      else if (e.key === "ArrowLeft") show(index - 1);
      else if (e.key === "ArrowRight") show(index + 1);
    });
  })();

  /* ---- Scroll-reveal for sections ---- */
  const revealTargets = document.querySelectorAll(
    ".section__head, .card, .split__copy, .split__media, .shot, .privacy__points, .privacy__table, .chips li, .trust__grid, .cta__inner"
  );
  revealTargets.forEach(function (el) { el.classList.add("reveal"); });

  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    revealTargets.forEach(function (el) { io.observe(el); });
  } else {
    revealTargets.forEach(function (el) { el.classList.add("is-in"); });
  }

  /* ---- Latest version from GitHub Releases ----
     Replaces the hardcoded version in the hero pill with the newest published
     release tag. Falls back silently to the static text if the request fails
     (offline, rate-limited, etc.). GitHub's unauthenticated API allows 60
     requests/hour per IP — fine for a marketing page. */
  const versionEl = document.getElementById("appVersion");
  if (versionEl && "fetch" in window) {
    fetch("https://api.github.com/repos/angoikon/byd-trip-stats/releases/latest", {
      headers: { Accept: "application/vnd.github+json" }
    })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (data) {
        const tag = data && data.tag_name;
        if (tag) versionEl.textContent = /^v/i.test(tag) ? tag : "v" + tag;
      })
      .catch(function () { /* keep the fallback text already in the HTML */ });
  }

  /* ---- Current year (footer, if needed later) ---- */
  const y = document.getElementById("year");
  if (y) y.textContent = String(new Date().getFullYear());
})();
