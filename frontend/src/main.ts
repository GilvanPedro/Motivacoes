import './styles.css';
import { api, type Tag } from './api';
import { gradientFor } from './colors';
import { Player, loadYouTubeApi } from './player';

const grid = document.querySelector<HTMLDivElement>('#tags')!;
const status = document.querySelector<HTMLParagraphElement>('#status')!;
const player = new Player();

void loadYouTubeApi();

function setStatus(html: string) {
  status.innerHTML = html;
  status.hidden = !html;
}

function render(tags: Tag[]) {
  const withVideos = tags.filter((t) => t.videoCount > 0);
  grid.replaceChildren();
  if (withVideos.length === 0) {
    setStatus('Nenhum vídeo cadastrado ainda. <a href="/admin.html">Adicionar o primeiro</a>');
    return;
  }
  setStatus('');
  for (const tag of withVideos) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'tag-button';
    btn.style.background = gradientFor(tag.id);
    const name = document.createElement('span');
    name.className = 'tag-name';
    name.textContent = tag.name;
    const count = document.createElement('span');
    count.className = 'tag-count';
    count.textContent = `${tag.videoCount} ${tag.videoCount === 1 ? 'vídeo' : 'vídeos'}`;
    btn.append(name, count);
    btn.addEventListener('click', () => {
      player.open((excludeId) => api.randomVideo(tag.id, excludeId), tag.name);
    });
    grid.append(btn);
  }
}

async function load() {
  // o servidor grátis "dorme"; avisa se demorar
  const slow = setTimeout(() => setStatus('Acordando o servidor… isso pode levar alguns segundos.'), 3000);
  try {
    render(await api.tags());
  } catch (e) {
    setStatus(`${e instanceof Error ? e.message : 'Erro ao carregar.'} <button type="button" class="link" id="retry">Tentar de novo</button>`);
    document.querySelector('#retry')?.addEventListener('click', () => void load());
  } finally {
    clearTimeout(slow);
  }
}

void load();
