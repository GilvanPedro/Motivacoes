import type { Video } from './api';

// --- YouTube IFrame API (tipagem mínima do que usamos) ---
interface YTPlayer {
  playVideo(): void;
  mute(): void;
  unMute(): void;
  setVolume(v: number): void;
  getPlayerState(): number;
  destroy(): void;
}
interface YTNamespace {
  Player: new (el: HTMLElement, opts: Record<string, unknown>) => YTPlayer;
}
declare global {
  interface Window {
    YT?: YTNamespace;
    onYouTubeIframeAPIReady?: () => void;
  }
}

const YT_ENDED = 0;
const YT_PLAYING = 1;
const YT_BUFFERING = 3;

let ytApi: Promise<YTNamespace> | null = null;

/** Carrega o script do YouTube uma vez só. Chame cedo para o play ser instantâneo. */
export function loadYouTubeApi(): Promise<YTNamespace> {
  ytApi ??= new Promise((resolve) => {
    if (window.YT?.Player) return resolve(window.YT);
    const previous = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      previous?.();
      resolve(window.YT!);
    };
    const script = document.createElement('script');
    script.src = 'https://www.youtube.com/iframe_api';
    script.async = true;
    document.head.append(script);
  });
  return ytApi;
}

// --- Fullscreen com prefixos (Safari) ---
type FsElement = HTMLElement & { webkitRequestFullscreen?: () => void };
type FsDocument = Document & { webkitFullscreenElement?: Element | null; webkitExitFullscreen?: () => void };

function fullscreenElement(): Element | null {
  const d = document as FsDocument;
  return d.fullscreenElement ?? d.webkitFullscreenElement ?? null;
}

function enterFullscreen(el: FsElement) {
  try {
    if (el.requestFullscreen) {
      el.requestFullscreen({ navigationUI: 'hide' }).catch(() => {});
    } else {
      el.webkitRequestFullscreen?.();
    }
  } catch {
    // iPhone não suporta fullscreen em <div>; o overlay já ocupa a tela toda.
  }
}

function exitFullscreen() {
  const d = document as FsDocument;
  if (!fullscreenElement()) return;
  if (d.exitFullscreen) d.exitFullscreen().catch(() => {});
  else d.webkitExitFullscreen?.();
}

export type VideoLoader = (excludeId?: number) => Promise<Video>;

/**
 * Overlay de tela cheia que toca um vídeo (YouTube ou arquivo enviado).
 * `open()` precisa ser chamado direto no clique, pro navegador permitir fullscreen e autoplay.
 */
export class Player {
  private readonly root: HTMLElement;
  private readonly stage: HTMLElement;
  private readonly label: HTMLElement;
  private readonly status: HTMLElement;
  private readonly nextBtn: HTMLButtonElement;
  private readonly soundBtn: HTMLButtonElement;

  private loader: VideoLoader | null = null;
  private current: Video | null = null;
  private yt: YTPlayer | null = null;
  private videoEl: HTMLVideoElement | null = null;
  private token = 0;
  private wasFullscreen = false;

  constructor() {
    this.root = document.createElement('div');
    this.root.className = 'player';
    this.root.hidden = true;
    this.root.innerHTML = `
      <div class="player-stage"></div>
      <p class="player-status" role="status"></p>
      <div class="player-bar">
        <span class="player-label"></span>
        <div class="player-actions">
          <button type="button" class="player-btn" data-action="sound" hidden>🔊 Ativar som</button>
          <button type="button" class="player-btn" data-action="next">↻ Outro</button>
          <button type="button" class="player-btn" data-action="close" aria-label="Fechar">✕</button>
        </div>
      </div>`;
    document.body.append(this.root);

    this.stage = this.root.querySelector('.player-stage')!;
    this.label = this.root.querySelector('.player-label')!;
    this.status = this.root.querySelector('.player-status')!;
    this.nextBtn = this.root.querySelector('[data-action="next"]')!;
    this.soundBtn = this.root.querySelector('[data-action="sound"]')!;

    this.nextBtn.addEventListener('click', () => void this.next());
    this.soundBtn.addEventListener('click', () => this.enableSound());
    this.root.querySelector('[data-action="close"]')!.addEventListener('click', () => this.close());

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !this.root.hidden) this.close();
    });
    const onFsChange = () => {
      if (fullscreenElement() === this.root) {
        this.wasFullscreen = true;
      } else if (this.wasFullscreen && !this.root.hidden) {
        // saiu da tela cheia (Esc / gesto do sistema) => fecha o player
        this.close();
      }
    };
    document.addEventListener('fullscreenchange', onFsChange);
    document.addEventListener('webkitfullscreenchange', onFsChange);
  }

  /** Abre o player com uma fonte de vídeos (ex.: aleatório de uma tag). */
  open(loader: VideoLoader, label: string, allowNext = true) {
    this.loader = loader;
    this.current = null;
    this.label.textContent = label;
    this.nextBtn.hidden = !allowNext;
    this.root.hidden = false;
    document.body.classList.add('no-scroll');
    this.wasFullscreen = false;
    enterFullscreen(this.root);
    void this.next();
  }

  /** Abre um vídeo específico (prévia no admin). */
  play(video: Video) {
    this.open(() => Promise.resolve(video), video.title, false);
  }

  close() {
    this.token++;
    this.clearStage();
    this.root.hidden = true;
    document.body.classList.remove('no-scroll');
    this.wasFullscreen = false;
    exitFullscreen();
  }

  private async next() {
    if (!this.loader) return;
    const token = ++this.token;
    this.clearStage();
    this.setStatus('Carregando…');
    try {
      const video = await this.loader(this.current?.id);
      if (token !== this.token) return;
      this.current = video;
      if (video.source === 'YOUTUBE' && video.youtubeId) await this.renderYouTube(video.youtubeId, token);
      else if (video.url) this.renderFile(video.url);
      else this.setStatus('Esse vídeo não tem um endereço válido.');
    } catch (e) {
      if (token === this.token) this.setStatus(e instanceof Error ? e.message : 'Erro ao carregar o vídeo.');
    }
  }

  private async renderYouTube(videoId: string, token: number) {
    const YT = await loadYouTubeApi();
    if (token !== this.token) return;
    const host = document.createElement('div');
    this.stage.append(host);
    this.yt = new YT.Player(host, {
      width: '100%',
      height: '100%',
      videoId,
      playerVars: { autoplay: 1, playsinline: 1, rel: 0, modestbranding: 1 },
      events: {
        onReady: (e: { target: YTPlayer }) => {
          if (token !== this.token) return;
          e.target.playVideo();
          // Se o navegador bloqueou o autoplay com som, toca mudo e oferece o botão de som.
          setTimeout(() => {
            if (token !== this.token || !this.yt) return;
            const state = this.yt.getPlayerState();
            if (state !== YT_PLAYING && state !== YT_BUFFERING) {
              this.yt.mute();
              this.yt.playVideo();
              this.soundBtn.hidden = false;
            }
          }, 1500);
        },
        onStateChange: (e: { data: number }) => {
          if (token !== this.token) return;
          if (e.data === YT_PLAYING) this.setStatus('');
          if (e.data === YT_ENDED) this.highlightNext();
        },
        onError: () => {
          if (token === this.token) this.setStatus('Esse vídeo não pode ser reproduzido aqui. Tenta outro.');
        },
      },
    });
  }

  private renderFile(url: string) {
    const video = document.createElement('video');
    video.src = url;
    video.controls = true;
    video.playsInline = true;
    video.autoplay = true;
    video.addEventListener('playing', () => this.setStatus(''));
    video.addEventListener('ended', () => this.highlightNext());
    video.addEventListener('error', () => this.setStatus('Não foi possível carregar o arquivo de vídeo.'));
    this.stage.append(video);
    this.videoEl = video;
    video.play().catch(() => {
      video.muted = true;
      this.soundBtn.hidden = false;
      video.play().catch(() => this.setStatus('Toque no vídeo para começar.'));
    });
  }

  private enableSound() {
    if (this.yt) {
      this.yt.unMute();
      this.yt.setVolume(100);
    }
    if (this.videoEl) this.videoEl.muted = false;
    this.soundBtn.hidden = true;
  }

  private highlightNext() {
    if (!this.nextBtn.hidden) this.nextBtn.classList.add('pulse');
  }

  private clearStage() {
    try {
      this.yt?.destroy();
    } catch {
      /* ignore */
    }
    this.yt = null;
    if (this.videoEl) {
      this.videoEl.pause();
      this.videoEl.removeAttribute('src');
      this.videoEl.load();
      this.videoEl = null;
    }
    this.stage.replaceChildren();
    this.soundBtn.hidden = true;
    this.nextBtn.classList.remove('pulse');
    this.setStatus('');
  }

  private setStatus(text: string) {
    this.status.textContent = text;
    this.status.hidden = !text;
  }
}
