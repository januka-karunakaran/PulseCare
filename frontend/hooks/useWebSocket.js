"use client";

import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { WS_BASE } from "@/utils/constants";

/**
 * Standardized STOMP WebSocket hook matching the Spring Boot backend WebSocket broker.
 * Automatically transmits Bearer token in CONNECT headers for server-side authorization.
 */
export function useWebSocket(token, userId) {
  const [connected, setConnected] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!token || !userId) return;

    const rawWsUrl = WS_BASE || "http://localhost:8080";
    const httpSchemeUrl = rawWsUrl
      .replace(/^ws:\/\//i, "http://")
      .replace(/^wss:\/\//i, "https://")
      .replace(/\/+$/, "");

    const endpoint = httpSchemeUrl.endsWith("/ws/notifications")
      ? httpSchemeUrl
      : `${httpSchemeUrl}/ws/notifications`;

    let client;
    try {
      client = new Client({
        webSocketFactory: () => new SockJS(endpoint),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str) => {
          if (process.env.NODE_ENV === "development") {
            console.log("[STOMP hook]", str);
          }
        },
      });

      client.onConnect = () => {
        setConnected(true);

        // Subscribe to user private notification destination with deduplication
        client.subscribe("/user/queue/notifications", (message) => {
          try {
            const data = JSON.parse(message.body);
            setNotifications((prev) => {
              if (data?.id && prev.some((item) => item.id === data.id)) {
                return prev;
              }
              return [data, ...prev];
            });
          } catch (err) {
            console.warn("Failed to parse incoming WebSocket notification:", err);
          }
        });
      };

      client.onDisconnect = () => {
        setConnected(false);
      };

      client.onStompError = (frame) => {
        console.warn("STOMP error:", frame.headers?.["message"]);
      };

      client.activate();
      clientRef.current = client;
    } catch (err) {
      console.warn("Error initializing WebSocket client:", err);
    }

    return () => {
      if (clientRef.current) {
        try {
          clientRef.current.deactivate();
        } catch {
          // ignore cleanup errors
        }
      }
    };
  }, [token, userId]);

  const emit = (destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination: destination,
        body: typeof body === "string" ? body : JSON.stringify(body),
      });
    }
  };

  return {
    connected,
    notifications,
    setNotifications,
    emit,
    client: clientRef.current,
  };
}
