import { Client } from '@stomp/stompjs';

/**
 * Creates and configures a STOMP over WebSocket client.
 *
 * @param {Object} options
 * @param {string} [options.token] - Optional JWT token
 * @param {Function} [options.onConnect] - Callback invoked when STOMP connection is established
 * @param {Function} [options.onDisconnect] - Callback invoked on disconnection
 * @param {Function} [options.onError] - Callback invoked on STOMP or WebSocket error
 * @returns {Client} Configured STOMP Client instance
 */
export function createStompClient({
  token = null,
  onConnect = null,
  onDisconnect = null,
  onError = null,
} = {}) {
  const protocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = typeof window !== 'undefined' ? window.location.host : 'localhost:8080';
  const defaultWsUrl = `${protocol}//${host}/ws`;
  const brokerURL = import.meta.env.VITE_WS_URL || defaultWsUrl;

  const authToken = token || (typeof window !== 'undefined' ? localStorage.getItem('token') : null);
  const connectHeaders = {};
  if (authToken) {
    connectHeaders.Authorization = `Bearer ${authToken}`;
  }

  const client = new Client({
    brokerURL,
    connectHeaders,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    debug: () => {
      // Intentionally quiet in production, can enable during active debugging
    },
    onConnect: (frame) => {
      if (onConnect) onConnect(frame);
    },
    onDisconnect: (frame) => {
      if (onDisconnect) onDisconnect(frame);
    },
    onStompError: (frame) => {
      if (onError) onError(frame);
    },
    onWebSocketClose: (event) => {
      if (onDisconnect) onDisconnect(event);
    },
    onWebSocketError: (event) => {
      if (onError) onError(event);
    },
  });

  return client;
}
