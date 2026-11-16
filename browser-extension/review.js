(() => {
  const id = "swipeclean-google-photos";
  const existing = document.getElementById(id);
  if (existing) {
    existing.dispatchEvent(new Event("swipeclean-close"));
    return;
  }

  const labels = {
    next: ["next", "next photo", "next item", "next video", "próximo", "próxima", "próxima foto",
      "próximo item", "próximo vídeo", "avançar", "siguiente", "suivant"],
    trash: ["move to trash", "move to bin", "delete", "delete photo", "move to the trash",
      "mover para a lixeira", "enviar para a lixeira", "excluir", "excluir foto", "mover para o lixo",
      "eliminar", "eliminar fotografia", "mover para o caixote do lixo",
      "mover a la papelera", "placer dans la corbeille"]
  };
  const normalize = value => value?.trim().normalize("NFC").toLowerCase();
  const supportedPage = url =>
    url.origin === location.origin && /\/photo\/[^/]+\/?$/.test(url.pathname);
  const currentPhoto = () => {
    const url = new URL(location.href);
    return supportedPage(url) ? url.pathname.replace(/\/$/, "") : "";
  };
  const visible = element => {
    const bounds = element.getBoundingClientRect();
    return bounds.width > 0 && bounds.height > 0 && element.checkVisibility({
      checkOpacity: true, checkVisibilityCSS: true
    });
  };
  function nativeButton(names) {
    const controls = document.querySelectorAll("button,[role='button']");
    const matches = Array.from(controls).filter(control => {
      if (!visible(control) || control.disabled || control.getAttribute("aria-disabled") === "true" ||
          control.closest("[role='dialog']")) return false;
      return [control.getAttribute("aria-label"), control.getAttribute("title"),
        control.getAttribute("data-tooltip")].some(name => names.includes(normalize(name)));
    });
    return matches.length === 1 ? matches[0] : null;
  }

  const host = document.createElement("aside");
  host.id = id;
  document.body.append(host);
  const root = host.attachShadow({ mode: "open" });
  root.innerHTML = `
    <style>
      :host { position: fixed; left: 50%; bottom: 24px; transform: translateX(-50%); z-index: 2147483647;
        width: min(550px, calc(100vw - 32px)); color: #f4f6ff; font: 14px system-ui,sans-serif; }
      section { box-sizing: border-box; background: #151e31; border: 1px solid #52607f;
        border-radius: 18px; padding: 14px; box-shadow: 0 10px 36px #0008; }
      header, .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
      strong { flex: 1; font-size: 16px; }
      button { background: #293652; color: inherit; border: 0; border-radius: 9px; padding: 9px 11px;
        font: 600 13px system-ui,sans-serif; cursor: pointer; }
      button:disabled { opacity: .42; cursor: default; }
      button:focus-visible, a:focus-visible { outline: 2px solid #a8b0ff; outline-offset: 2px; }
      button[data-action="remove"] { background: #793148; }
      button[data-action="keep"] { background: #125d53; }
      button[data-action="trash"] { background: #793148; }
      .drag { text-align: center; background: #202c45; margin: 8px 0; padding: 11px; border-radius: 9px;
        touch-action: none; user-select: none; cursor: grab; }
      .drag:active { cursor: grabbing; }
      p { margin: 9px 0; line-height: 1.4; color: #c9d3ef; }
      [hidden] { display: none !important; }
      ol { max-height: 32vh; overflow: auto; padding-left: 24px; }
      li { margin-bottom: 7px; }
      li button { margin-left: 8px; padding: 4px 7px; }
      a { color: #b5c1ff; }
    </style>
    <section aria-label="SwipeClean">
      <header>
        <strong>SwipeClean · Google Fotos</strong>
        <button data-action="close" aria-label="Fechar SwipeClean">✕</button>
      </header>
      <p id="status" role="status"></p>
      <div id="drag" class="drag" hidden>← Marcar para descarte · deslize aqui · Manter →</div>
      <div class="actions">
        <button data-action="remove">← Descartar</button>
        <button data-action="keep">Manter →</button>
        <button data-action="undo">↶ Desfazer</button>
        <button data-action="review">Revisar (0)</button>
      </div>
      <div id="queue" hidden>
        <ol id="marked"></ol>
        <p>Abra um item da lista e use o botão abaixo para acionar a lixeira do Google Fotos.</p>
        <button data-action="trash">Mover foto aberta para a lixeira</button>
      </div>
    </section>`;

  const $ = selector => root.querySelector(selector);
  const button = action => $("[data-action='" + action + "']");
  const status = $("#status");
  const drag = $("#drag");
  const queue = $("#queue");
  const marked = $("#marked");
  const history = [];
  const storageKey = "swipeclean:" + (location.pathname.match(/^\/u\/\d+/)?.[0] || "/");
  let decisions = {};
  let loaded = false;
  let lastPhoto = "";
  let pendingTrash = "";
  let message = "";
  let dragX;
  let dragY;
  let timer;

  function render() {
    const photo = currentPhoto();
    const discard = Object.entries(decisions).filter(([, value]) => value === "remove");
    status.textContent = message || (photo ? "Revise a mídia aberta."
      : "Abra uma foto ou um vídeo da sua biblioteca para começar.");
    drag.hidden = !photo;
    for (const action of ["keep", "remove"]) button(action).disabled = !photo || !loaded;
    button("undo").disabled = !history.length;
    button("review").textContent = "Revisar (" + discard.length + ")";
    button("trash").disabled = !photo || decisions[photo] !== "remove" || !!pendingTrash;
    marked.replaceChildren();
    discard.forEach(([path], index) => {
      const url = new URL(path, location.origin);
      if (!supportedPage(url)) return;
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = url.href;
      link.textContent = path === photo ? "Mídia " + (index + 1) + " · aberta" : "Abrir mídia " + (index + 1);
      const clear = document.createElement("button");
      clear.textContent = "Retirar da lista";
      clear.addEventListener("click", () => {
        history.push({ photo: path, previous: decisions[path] });
        delete decisions[path];
        save();
        render();
      });
      item.append(link, clear);
      marked.append(item);
    });
  }

  function save() {
    chrome.storage.local.set({ [storageKey]: decisions }).catch(() => {
      message = "Não foi possível salvar as marcações no navegador.";
      render();
    });
  }

  function decide(value) {
    const photo = currentPhoto();
    if (!photo || !loaded || pendingTrash) return;
    history.push({ photo, previous: decisions[photo] });
    decisions[photo] = value;
    save();
    const next = nativeButton(labels.next);
    const result = value === "keep" ? "Mantida." : "Marcada para descarte.";
    message = next ? result : result + " Use a seta do Google Fotos para abrir a próxima mídia.";
    render();
    if (next) next.click();
  }

  function undo() {
    const action = history.pop();
    if (!action) return;
    if (action.previous) decisions[action.photo] = action.previous;
    else delete decisions[action.photo];
    pendingTrash = "";
    save();
    message = "Última marcação desfeita.";
    render();
    if (currentPhoto() !== action.photo) {
      const direct = document.createElement("a");
      direct.href = action.photo;
      direct.textContent = " Voltar à mídia";
      status.append(direct);
    }
  }

  function trashCurrent() {
    const photo = currentPhoto();
    if (!photo || decisions[photo] !== "remove" || pendingTrash) return;
    const control = nativeButton(labels.trash);
    if (!control) {
      message = "Use o ícone de lixeira do Google Fotos para mover esta mídia.";
      render();
      return;
    }
    if (!window.confirm("Mover a mídia aberta para a lixeira do Google Fotos?")) return;
    pendingTrash = photo;
    message = "Confira a confirmação do Google Fotos, se aparecer.";
    control.click();
    render();
    window.setTimeout(() => {
      if (pendingTrash === photo) {
        pendingTrash = "";
        message = "Confira o resultado no Google Fotos. A marcação continua na lista.";
        render();
      }
    }, 12000);
  }

  function close() {
    clearInterval(timer);
    host.remove();
  }

  button("close").addEventListener("click", close);
  host.addEventListener("swipeclean-close", close, { once: true });
  button("remove").addEventListener("click", () => decide("remove"));
  button("keep").addEventListener("click", () => decide("keep"));
  button("undo").addEventListener("click", undo);
  button("review").addEventListener("click", () => { queue.hidden = !queue.hidden; });
  button("trash").addEventListener("click", trashCurrent);
  drag.addEventListener("pointerdown", event => {
    dragX = event.clientX;
    dragY = event.clientY;
    drag.setPointerCapture(event.pointerId);
  });
  drag.addEventListener("pointerup", event => {
    const delta = event.clientX - dragX;
    if (Math.abs(delta) > 50 && Math.abs(delta) > Math.abs(event.clientY - dragY)) {
      decide(delta < 0 ? "remove" : "keep");
    }
  });
  timer = setInterval(() => {
    const photo = currentPhoto();
    if (lastPhoto === photo) return;
    lastPhoto = photo;
    if (pendingTrash) {
      pendingTrash = "";
      message = "Confira a lixeira no Google Fotos. A marcação continua na lista.";
    } else {
      message = "";
    }
    render();
  }, 250);
  render();
  chrome.storage.local.get(storageKey).then(saved => {
    decisions = saved[storageKey] || {};
    loaded = true;
    render();
  }).catch(() => {
    message = "Não foi possível abrir as marcações do navegador.";
    render();
  });
})();
