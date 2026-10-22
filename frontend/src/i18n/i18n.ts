import i18next, { type i18n } from 'i18next';
import { en } from './catalogue';

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: { translation: typeof en };
  }
}

/** One i18n instance per tab, ready on return. It reaches components only through `<I18nextProvider>`; there is no
 * global instance (initReactI18next is deliberately not used). English only at launch, falling back to English (O3). */
export function createI18n(): i18n {
  const instance = i18next.createInstance();
  void instance.init({
    lng: 'en',
    fallbackLng: 'en',
    resources: { en: { translation: en } },
    interpolation: { escapeValue: false },
    initAsync: false,
  });
  return instance;
}
