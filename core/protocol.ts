/**
 * Idroid Core Protocol Specification
 * 
 * Defines the wire protocol between Desktop (macOS)
 * and Mobile (Android) companion applications.
 * 
 * Principles:
 * - Simple JSON over local TCP/TLS sockets.
 * - Anti-slop: clean, human-friendly message types.
 * - Zero cloud dependencies: 100% local peer-to-peer.
 */

export const PROTOCOL_VERSION = "1.0.0";

/** Default TCP port allocation */
export const IDROID_PORTS = {
  /** Web pairing & QR handshake fallback endpoint */
  HTTP_PAIRING: 53100,
  /** Cryptographic discovery & authentication */
  DISCOVERY: 53101,
  /** Main bidirectional control channel (notifications, clipboard, battery, calls) */
  CONTROL_CHANNEL: 53102,
  /** Mirroring control and status callbacks */
  MIRROR_CONTROL: 53103,
  /** Low-latency raw H.264/H.265 video stream from scrcpy */
  MIRROR_STREAM: 53104,
  /** Ultra-fast direct file transfer socket (Wi-Fi Direct / Local socket) */
  FILE_TRANSFER: 53109,
} as const;

/** Core Message Envelope */
export interface IdroidMessage<T = unknown> {
  id: string;
  type: MessageType;
  timestamp: number;
  payload: T;
}

export type MessageType =
  // Connection & Handshake
  | "ping"
  | "pong"
  | "handshake_request"
  | "handshake_response"
  | "device_info"
  | "disconnect"

  // Clipboard
  | "clipboard_update"
  | "clipboard_ack"

  // Notifications
  | "notification_posted"
  | "notification_removed"
  | "notification_action_reply"

  // Screen Mirroring & Control
  | "mirror_start_request"
  | "mirror_started"
  | "mirror_stopped"
  | "input_touch_event"
  | "input_key_event"
  | "input_back_button"
  | "input_home_button"

  // Camera & Webcam
  | "camera_start_request"
  | "camera_started"
  | "camera_stopped"
  | "camera_lens_changed"

  // File Transfer (Quick Share)
  | "file_offer"
  | "file_accept"
  | "file_reject"
  | "file_progress"
  | "file_complete"

  // Phone State
  | "battery_status"
  | "phone_call_incoming"
  | "phone_call_answered"
  | "phone_call_ended";

/* ---------------- Payload Types ---------------- */

export interface DeviceInfoPayload {
  deviceId: string;
  deviceName: string;
  platform: "android" | "macos";
  osVersion: string;
  appVersion: string;
  ipAddress: string;
  batteryLevel?: number;
  isCharging?: boolean;
}

export interface ClipboardPayload {
  content: string;
  contentType: "text" | "url" | "image";
  sourceDeviceId: string;
  /** Unique hash to avoid infinite echo loops between paired devices */
  hash: string;
}

export interface NotificationPayload {
  notificationId: string;
  packageName: string;
  appName: string;
  title: string;
  text: string;
  subText?: string;
  timestamp: number;
  hasReplyAction: boolean;
  actions?: Array<{
    actionId: string;
    label: string;
  }>;
}

export interface NotificationReplyPayload {
  notificationId: string;
  replyText: string;
}

export interface MirrorConfigPayload {
  maxFps: number;
  bitrateBps: number;
  maxDimension: number; // e.g. 1920 or 2560
  includeAudio: boolean;
}

export interface FileOfferPayload {
  transferId: string;
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  sha256?: string;
}

export interface FileProgressPayload {
  transferId: string;
  bytesTransferred: number;
  totalBytes: number;
  speedBytesPerSec: number;
}
