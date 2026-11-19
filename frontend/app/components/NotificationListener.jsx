"use client";

import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getUser, getAccessToken } from "@/utils/auth";
import { WS_BASE } from "@/utils/constants";

export default function NotificationListener({ onNotificationReceived }) {
  const stompClientRef = useRef(null);
  const userId = getUser()?.userId;

  useEffect(() => {
    if (!userId) return;

    // SockJS is an HTTP fallback transport and strictly requires http:// or https:// scheme
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
      const token = getAccessToken();
      client = new Client({
        webSocketFactory: () => new SockJS(endpoint),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        debug: (str) => {
          if (process.env.NODE_ENV === "development") {
            console.log("[STOMP]", str);
          }
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
      });

      client.onConnect = (frame) => {
        console.log("Connected to WebSocket notifications:", frame?.headers?.["user-name"] || userId);

        // Subscribe to user's private notification queue
        // Spring convertAndSendToUser sends to /user/{username}/queue/notifications
        client.subscribe(`/user/${userId}/queue/notifications`, (message) => {
          try {
            const notification = JSON.parse(message.body);
            if (onNotificationReceived) {
              onNotificationReceived(notification);
            }
          } catch (err) {
            console.warn("Failed to parse incoming notification:", err);
          }
        });
      };

      client.onStompError = (frame) => {
        console.warn("STOMP Broker reported error:", frame.headers?.["message"]);
      };

      client.onWebSocketError = (event) => {
        console.warn("WebSocket connection warning (non-fatal):", event?.type || event);
      };

      client.activate();
      stompClientRef.current = client;
    } catch (err) {
      console.warn("Failed to initialize WebSocket client:", err);
    }

    return () => {
      if (stompClientRef.current) {
        try {
          stompClientRef.current.deactivate();
        } catch {
          // Ignore cleanup errors
        }
      }
    };
  }, [onNotificationReceived, userId]);

  return null;
}
