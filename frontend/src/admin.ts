import './styles.css';
import { ApiError, api, auth, uploadFile, type Tag, type Video, type VideoSource } from './api';
import { Player, loadYouTubeApi } from './player';

const $ = <T extends HTMLElement>(sel: string) => document.querySelector<T>(sel)!;

const loginSection = $('#login-section');
const loginForm = $<HTMLFormElement>('#login-form');
const loginError = $('#login-error');
const passwordInput = $<HTMLInputElement>('#password');
const app = $('#app');
const logoutBtn = $<HTMLButtonElement>('#logout');

const videoForm = $<HTMLFormElement>('#video-form');
const urlField = $('#url-field');
const fileField = $('#file-field');
const urlInput = $<HTMLInputElement>('#video-url');
const fileInput = $<HTMLInputElement>('#video-file');
const titleInput = $<HTMLInputElement>('#video-title');
const uploadDisabled = $('#upload-disabled');
const videoTags = $('#video-tags');
const tagNameInput = $<HTMLInputElement>('#tag-name');
const tagCreateBtn = $<HTMLButtonElement>('#tag-create');
const progress = $<HTMLProgressElement>('#upload-progress');
const submitBtn = $<HTMLButtonElement>('#video-submit');
const videoMessage = $('#video-message');

const filterSelect = $<HTMLSelectElement>('#filter');
const videoList = $<HTMLUListElement>('#video-list');
const tagList = $('#tag-list');
const toast = $('#toast');

const player = new Player();
void loadYouTubeApi();

let tags: Tag[] = [];
let videos: Video[] = [];
let mode: VideoSource = 'YOUTUBE';
let uploadsEnabled = false;
const selectedTags = new Set<number>();
let editingId: number | null = null;

// ---------- utilidades ----------

let toastTimer: number | undefined;
function showToast(text: string, isError = false) {
  toast.textContent = text;
  toast.classList.toggle('error', isError);
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (toast.hidden = true), 3500);
}

/** Executa uma ação da API; se a senha ficou inválida, volta pra tela de login. */
async function guard<T>(fn: () => Promise<T>): Promise<T | undefined> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      auth.clear();
      showLogin('Sua sessão expirou. Entre novamente.');
      return undefined;
    }
    showToast(e instanceof Error ? e.message : 'Algo deu errado.', true);
    return undefined;
  }
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, text?: string) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function tagToggle(tag: Tag, selected: Set<number>, onChange?: () => void) {
  const btn = el('button', 'chip toggle', tag.name);
  btn.type = 'button';
  btn.setAttribute('aria-pressed', String(selected.has(tag.id)));
  btn.addEventListener('click', () => {
    if (selected.has(tag.id)) selected.delete(tag.id);
    else selected.add(tag.id);
    btn.setAttribute('aria-pressed', String(selected.has(tag.id)));
    onChange?.();
  });
  return btn;
}

// ---------- login ----------

function showLogin(message = '') {
  app.hidden = true;
  logoutBtn.hidden = true;
  loginSection.hidden = false;
  loginError.textContent = message;
  passwordInput.focus();
}

loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  loginError.textContent = '';
  try {
    auth.set(await api.login(passwordInput.value));
    passwordInput.value = '';
    await start();
  } catch (err) {
    loginError.textContent = err instanceof Error ? err.message : 'Erro ao entrar.';
  }
});

logoutBtn.addEventListener('click', () => {
  auth.clear();
  showLogin();
});

// ---------- tags ----------

function renderTags() {
  // seletor do formulário
  videoTags.replaceChildren(...tags.map((t) => tagToggle(t, selectedTags)));
  if (tags.length === 0) videoTags.append(el('p', 'hint', 'Crie a primeira tag abaixo.'));

  // filtro
  const current = filterSelect.value;
  filterSelect.replaceChildren(new Option('Todas as tags', ''));
  for (const t of tags) filterSelect.append(new Option(`${t.name} (${t.videoCount})`, String(t.id)));
  filterSelect.value = tags.some((t) => String(t.id) === current) ? current : '';

  // lista de tags com botão de apagar
  tagList.replaceChildren();
  if (tags.length === 0) tagList.append(el('p', 'hint', 'Nenhuma tag ainda.'));
  for (const t of tags) {
    const chip = el('span', 'chip');
    chip.append(el('span', '', t.name), el('span', 'chip-count', String(t.videoCount)));
    const del = el('button', 'chip-remove', '×');
    del.type = 'button';
    del.setAttribute('aria-label', `Apagar tag ${t.name}`);
    del.addEventListener('click', async () => {
      if (!confirm(`Apagar a tag "${t.name}"? Os vídeos continuam cadastrados.`)) return;
      const ok = await guard(async () => {
        await api.deleteTag(t.id);
        return true;
      });
      if (!ok) return;
      selectedTags.delete(t.id);
      await refresh();
      showToast('Tag apagada.');
    });
    chip.append(del);
    tagList.append(chip);
  }
}

async function createTag() {
  const name = tagNameInput.value.trim();
  if (!name) {
    tagNameInput.focus();
    return;
  }
  tagCreateBtn.disabled = true;
  const tag = await guard(() => api.createTag(name));
  tagCreateBtn.disabled = false;
  if (!tag) return;
  tagNameInput.value = '';
  selectedTags.add(tag.id);
  await loadTags();
  showToast(`Tag "${tag.name}" criada.`);
}

tagCreateBtn.addEventListener('click', () => void createTag());
tagNameInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    void createTag();
  }
});

// ---------- formulário de vídeo ----------

function setMode(next: VideoSource) {
  mode = next;
  document.querySelectorAll<HTMLButtonElement>('.tab').forEach((tab) => {
    const active = tab.dataset.mode === next;
    tab.classList.toggle('active', active);
    tab.setAttribute('aria-selected', String(active));
  });
  urlField.hidden = next !== 'YOUTUBE';
  fileField.hidden = next !== 'UPLOAD' || !uploadsEnabled;
  uploadDisabled.hidden = next !== 'UPLOAD' || uploadsEnabled;
  submitBtn.disabled = next === 'UPLOAD' && !uploadsEnabled;
  titleInput.placeholder = next === 'YOUTUBE' ? 'Pega do YouTube automaticamente' : 'Usa o nome do arquivo';
}

document.querySelectorAll<HTMLButtonElement>('.tab').forEach((tab) =>
  tab.addEventListener('click', () => setMode(tab.dataset.mode as VideoSource)),
);

function setBusy(busy: boolean) {
  submitBtn.disabled = busy;
  submitBtn.textContent = busy ? 'Salvando…' : 'Adicionar vídeo';
}

videoForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  videoMessage.textContent = '';
  videoMessage.classList.remove('error');
  const fail = (msg: string) => {
    videoMessage.textContent = msg;
    videoMessage.classList.add('error');
  };

  if (selectedTags.size === 0) return fail('Selecione pelo menos uma tag.');
  const tagIds = [...selectedTags];
  const title = titleInput.value.trim() || undefined;

  setBusy(true);
  try {
    if (mode === 'YOUTUBE') {
      const youtubeUrl = urlInput.value.trim();
      if (!youtubeUrl) return fail('Cole o link do vídeo.');
      await api.createVideo({ source: 'YOUTUBE', youtubeUrl, title, tagIds });
      urlInput.value = '';
    } else {
      const file = fileInput.files?.[0];
      if (!file) return fail('Escolha um arquivo de vídeo.');
      const contentType = file.type || 'video/mp4';
      const { uploadUrl, key } = await api.presignUpload(file.name, contentType);
      progress.hidden = false;
      progress.value = 0;
      await uploadFile(uploadUrl, file, contentType, (p) => {
        progress.value = p;
        submitBtn.textContent = `Enviando… ${p}%`;
      });
      await api.createVideo({
        source: 'UPLOAD',
        storageKey: key,
        title: title ?? file.name.replace(/\.[^.]+$/, ''),
        tagIds,
      });
      fileInput.value = '';
    }
    titleInput.value = '';
    showToast('Vídeo adicionado!');
    await refresh();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      auth.clear();
      showLogin('Sua sessão expirou. Entre novamente.');
    } else {
      fail(err instanceof Error ? err.message : 'Erro ao salvar.');
    }
  } finally {
    setBusy(false);
    progress.hidden = true;
    setMode(mode);
  }
});

// ---------- lista de vídeos ----------

function thumbnail(v: Video) {
  if (v.source === 'YOUTUBE' && v.youtubeId) {
    const img = el('img', 'thumb');
    img.src = `https://i.ytimg.com/vi/${v.youtubeId}/mqdefault.jpg`;
    img.alt = '';
    img.loading = 'lazy';
    return img;
  }
  return el('div', 'thumb thumb-file', '🎬');
}

function renderVideos() {
  videoList.replaceChildren();
  if (videos.length === 0) {
    videoList.append(el('li', 'hint', 'Nenhum vídeo por aqui.'));
    return;
  }
  for (const v of videos) videoList.append(v.id === editingId ? editRow(v) : viewRow(v));
}

function viewRow(v: Video) {
  const li = el('li', 'video-item');
  const playBtn = el('button', 'thumb-btn');
  playBtn.type = 'button';
  playBtn.setAttribute('aria-label', `Assistir ${v.title}`);
  playBtn.append(thumbnail(v));
  playBtn.addEventListener('click', () => player.play(v));

  const info = el('div', 'video-info');
  info.append(el('p', 'video-title', v.title));
  const chips = el('div', 'chips small');
  for (const t of v.tags) chips.append(el('span', 'chip', t.name));
  chips.append(el('span', 'chip source', v.source === 'YOUTUBE' ? 'YouTube' : 'Arquivo'));
  info.append(chips);

  const actions = el('div', 'video-actions');
  const edit = el('button', 'btn ghost small', 'Editar');
  edit.type = 'button';
  edit.addEventListener('click', () => {
    editingId = v.id;
    renderVideos();
  });
  const del = el('button', 'btn danger small', 'Excluir');
  del.type = 'button';
  del.addEventListener('click', async () => {
    if (!confirm(`Excluir "${v.title}"?`)) return;
    del.disabled = true;
    const ok = await guard(async () => {
      await api.deleteVideo(v.id);
      return true;
    });
    if (!ok) {
      del.disabled = false;
      return;
    }
    showToast('Vídeo excluído.');
    await refresh();
  });
  actions.append(edit, del);

  li.append(playBtn, info, actions);
  return li;
}

function editRow(v: Video) {
  const li = el('li', 'video-item editing');
  const selected = new Set(v.tags.map((t) => t.id));

  const info = el('div', 'video-info');
  const title = el('input');
  title.value = v.title;
  title.maxLength = 200;
  title.setAttribute('aria-label', 'Título');
  const chips = el('div', 'chips small');
  chips.append(...tags.map((t) => tagToggle(t, selected)));
  info.append(title, chips);

  const actions = el('div', 'video-actions');
  const save = el('button', 'btn primary small', 'Salvar');
  save.type = 'button';
  save.addEventListener('click', async () => {
    if (selected.size === 0) return showToast('Selecione pelo menos uma tag.', true);
    save.disabled = true;
    const updated = await guard(() => api.updateVideo(v.id, { title: title.value, tagIds: [...selected] }));
    save.disabled = false;
    if (!updated) return;
    editingId = null;
    showToast('Vídeo atualizado.');
    await refresh();
  });
  const cancel = el('button', 'btn ghost small', 'Cancelar');
  cancel.type = 'button';
  cancel.addEventListener('click', () => {
    editingId = null;
    renderVideos();
  });
  actions.append(save, cancel);

  li.append(thumbnail(v), info, actions);
  return li;
}

filterSelect.addEventListener('change', () => void loadVideos());

// ---------- carregamento ----------

async function loadTags() {
  const result = await guard(() => api.tags());
  if (!result) return;
  tags = result;
  for (const id of [...selectedTags]) if (!tags.some((t) => t.id === id)) selectedTags.delete(id);
  renderTags();
}

async function loadVideos() {
  const tagId = filterSelect.value ? Number(filterSelect.value) : undefined;
  const result = await guard(() => api.videos(tagId));
  if (!result) return;
  videos = result;
  renderVideos();
}

async function refresh() {
  await loadTags();
  await loadVideos();
}

async function start() {
  loginSection.hidden = true;
  app.hidden = false;
  logoutBtn.hidden = false;
  const config = await guard(() => api.config());
  uploadsEnabled = config?.uploadsEnabled ?? false;
  setMode(mode);
  await refresh();
}

if (auth.get()) {
  api.checkSession().then(
    () => start(),
    (e) => {
      if (e instanceof ApiError && e.status === 401) auth.clear();
      showLogin(e instanceof ApiError && e.status === 401 ? '' : e.message);
    },
  );
} else {
  showLogin();
}
