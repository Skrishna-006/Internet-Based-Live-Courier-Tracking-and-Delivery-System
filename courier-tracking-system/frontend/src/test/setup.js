import '@testing-library/jest-dom';

// JSDOM Leaflet polyfills
if (typeof window !== 'undefined') {
  if (typeof globalThis.AbortController !== 'undefined') {
    window.AbortController = globalThis.AbortController;
  }
  if (typeof globalThis.AbortSignal !== 'undefined') {
    window.AbortSignal = globalThis.AbortSignal;
  }

  // Mock getBoundingClientRect if needed
  if (!Element.prototype.getBoundingClientRect) {
    Element.prototype.getBoundingClientRect = () => ({
      width: 500,
      height: 500,
      top: 0,
      left: 0,
      bottom: 500,
      right: 500,
    });
  }

  // Mock matchMedia
  if (!window.matchMedia) {
    window.matchMedia = (query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    });
  }
}
