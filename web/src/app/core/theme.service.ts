import { Injectable, signal } from '@angular/core';

export type Theme = 'dark' | 'light';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private static readonly KEY = 'pv_theme';

  readonly theme = signal<Theme>(this.load());

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    localStorage.setItem(ThemeService.KEY, next);
    this.apply(next);
  }

  set(t: Theme): void {
    this.theme.set(t);
    localStorage.setItem(ThemeService.KEY, t);
    this.apply(t);
  }

  private load(): Theme {
    const stored = localStorage.getItem(ThemeService.KEY);
    if (stored === 'light' || stored === 'dark') return stored;
    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }

  private apply(t: Theme): void {
    document.documentElement.setAttribute('data-theme', t);
  }
}
