// shared.jsx — Show Up shared UI primitives.
// Light-themed, reading from --liq-* tokens. Exposed on window so other
// JSX bundles can pick them up.

const { useState, useEffect, useRef, useMemo, useCallback, createContext, useContext } = React;

// ─────────────────────────────────────────────────────────────
// Brand
// ─────────────────────────────────────────────────────────────
function Wordmark({ size = 22, withDot = true, style = {} }) {
  return (
    <span className="su-wordmark" style={{ fontSize: size, lineHeight: 1, ...style }}>
      <span>Show</span>
      <span className="su-up">Up</span>
      {withDot && <span className="su-dot" style={{ fontFamily: 'var(--liq-font-serif)', fontStyle: 'normal' }}>.</span>}
    </span>
  );
}

// ─────────────────────────────────────────────────────────────
// Lucide-style stroke icons (drawn inline so we don't load a script)
// ─────────────────────────────────────────────────────────────
const SVG_BASE = {
  width: 20, height: 20, viewBox: '0 0 24 24',
  fill: 'none', stroke: 'currentColor', strokeWidth: 1.7,
  strokeLinecap: 'round', strokeLinejoin: 'round',
};
function Icon({ name, size = 20, stroke = 1.7, style = {} }) {
  const paths = ICONS[name];
  if (!paths) return null;
  return (
    <svg {...SVG_BASE} width={size} height={size} strokeWidth={stroke}
      style={{ display: 'block', flex: 'none', ...style }}>
      {paths}
    </svg>
  );
}
const ICONS = {
  'arrow-right': <><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></>,
  'arrow-left':  <><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></>,
  'arrow-down':  <><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></>,
  'external-link': <><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></>,
  'chevron-left':  <polyline points="15 18 9 12 15 6"/>,
  'chevron-right': <polyline points="9 18 15 12 9 6"/>,
  'chevron-down':  <polyline points="6 9 12 15 18 9"/>,
  'check':         <polyline points="20 6 9 17 4 12"/>,
  'x':             <><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></>,
  'heart':         <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>,
  'heart-filled':  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" fill="currentColor"/>,
  'clock':         <><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></>,
  'zap':           <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>,
  'star':          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>,
  'shield':        <path d="M12 2 4 5v6c0 5 3.4 9.5 8 11 4.6-1.5 8-6 8-11V5l-8-3z"/>,
  'shield-check':  <><path d="M12 2 4 5v6c0 5 3.4 9.5 8 11 4.6-1.5 8-6 8-11V5l-8-3z"/><polyline points="9 12 11 14 15 10"/></>,
  'map-pin':       <><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></>,
  'map':           <><polygon points="3 7 9 4 15 7 21 4 21 17 15 20 9 17 3 20 3 7"/><line x1="9" y1="4" x2="9" y2="17"/><line x1="15" y1="7" x2="15" y2="20"/></>,
  'camera':        <><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></>,
  'video':         <><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></>,
  'mic':           <><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0"/><line x1="12" y1="18" x2="12" y2="22"/><line x1="8" y1="22" x2="16" y2="22"/></>,
  'upload':        <><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></>,
  'trash':         <><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></>,
  'refresh':       <><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></>,
  'edit':          <><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z"/></>,
  'lightbulb':     <><path d="M9 18h6"/><path d="M10 22h4"/><path d="M12 2a7 7 0 0 0-4 12.74V17h8v-2.26A7 7 0 0 0 12 2z"/></>,
  'play':          <polygon points="6 4 20 12 6 20 6 4"/>,
  'play-circle':   <><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></>,
  'plus':          <><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></>,
  'minus':         <line x1="5" y1="12" x2="19" y2="12"/>,
  'user':          <><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></>,
  'users':         <><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></>,
  'settings':      <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.36.16.66.42.88.74.22.32.34.7.34 1.09 0 .39-.12.77-.34 1.09-.22.32-.52.58-.88.74z"/></>,
  'bell':          <><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></>,
  'lock':          <><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></>,
  'eye':           <><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></>,
  'eye-off':       <><path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a18.05 18.05 0 0 1 4.06-5.94"/><path d="M9.9 4.24A10.07 10.07 0 0 1 12 4c7 0 11 8 11 8a18.05 18.05 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></>,
  'share':         <><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></>,
  'send':          <><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></>,
  'sliders':       <><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></>,
  'filter':        <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>,
  'search':        <><circle cx="11" cy="11" r="7"/><line x1="16.65" y1="16.65" x2="21" y2="21"/></>,
  'binoculars':    <><rect x="3" y="7" width="6" height="13" rx="3"/><rect x="15" y="7" width="6" height="13" rx="3"/><path d="M9 6.5a1.5 1.5 0 0 1 3 0v3.5M15 6.5a1.5 1.5 0 0 0-3 0"/><line x1="9" y1="13.5" x2="15" y2="13.5"/></>,
  'copy':          <><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></>,
  'sparkles':      <><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2 2M16.4 16.4l2 2M5.6 18.4l2-2M16.4 7.6l2-2"/></>,
  'compass':       <><circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/></>,
  'phone':         <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81.72A2 2 0 0 1 22 16.92z"/>,
  'message-circle': <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>,
  'whatsapp':      <><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/><path d="M9.4 8.2c-.6 0-1.2.6-1.2 1.4 0 1.3.9 2.6 1 2.8.2.2 1.8 2.9 4.5 4 .7.3 1.3.3 1.7.2.5-.1 1.4-.6 1.6-1.2.2-.6.2-1.1.1-1.2 0-.1-.2-.2-.5-.3l-1.4-.7c-.2-.1-.4 0-.5.1l-.6.8c-.1.1-.3.2-.5.1-.7-.3-1.4-.7-1.9-1.3-.4-.4-.8-.9-1-1.4-.1-.2 0-.4.1-.5l.5-.6c.1-.1.1-.3.1-.4l-.6-1.5c-.2-.5-.4-.5-.6-.5z"/></>,
  'gift':          <><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5"/><line x1="12" y1="22" x2="12" y2="7"/><path d="M12 7H7.5a2.5 2.5 0 1 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/></>,
  'coffee':        <><path d="M18 8h1a4 4 0 0 1 0 8h-1"/><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"/><line x1="6" y1="1" x2="6" y2="4"/><line x1="10" y1="1" x2="10" y2="4"/><line x1="14" y1="1" x2="14" y2="4"/></>,
  'navigation':    <polygon points="3 11 22 2 13 21 11 13 3 11"/>,
  'walking':       <><circle cx="13" cy="4" r="1.7"/><path d="M13 6v6"/><path d="M13 12l3 4v5"/><path d="M13 12l-3 4-1 5"/><path d="M13 8l4 1.2"/><path d="M13 8l-3-1"/></>,
  'car':           <><path d="M5 11l1.6-4.3A2 2 0 0 1 8.5 5.4h7a2 2 0 0 1 1.9 1.3L19 11"/><path d="M4 11h16a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-1.5a1 1 0 0 1-1-1v-.8H6.5v.8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z"/><circle cx="7.2" cy="13.6" r="0.6"/><circle cx="16.8" cy="13.6" r="0.6"/></>,
  'pause':         <><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></>,
  'more':          <><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/><circle cx="5" cy="12" r="1.5"/></>,
  'flame':         <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>,
  'sun':           <><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></>,
  'smile':         <><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9.2" x2="9.01" y2="9.2"/><line x1="15" y1="9.2" x2="15.01" y2="9.2"/></>,
  'frown':         <><circle cx="12" cy="12" r="10"/><path d="M16 16s-1.5-2-4-2-4 2-4 2"/><line x1="9" y1="9.2" x2="9.01" y2="9.2"/><line x1="15" y1="9.2" x2="15.01" y2="9.2"/></>,
  'hand':          <><path d="M18 11V6a2 2 0 0 0-2-2 2 2 0 0 0-2 2"/><path d="M14 10V4a2 2 0 0 0-2-2 2 2 0 0 0-2 2v2"/><path d="M10 10.5V6a2 2 0 0 0-2-2 2 2 0 0 0-2 2v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/></>,
  'flag':          <><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/></>,
  'shield-alert':  <><path d="M12 2 4 5v6c0 5 3.4 9.5 8 11 4.6-1.5 8-6 8-11V5l-8-3z"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="15.5" x2="12.01" y2="15.5"/></>,
  'user-x':        <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="17" y1="8" x2="22" y2="13"/><line x1="22" y1="8" x2="17" y2="13"/></>,
  'user-plus':     <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></>,
  'book-user':     <><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/><circle cx="12" cy="9" r="2"/><path d="M9 15a3 3 0 0 1 6 0"/></>,
  'alert-circle':  <><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></>,
  'phone-call':    <><path d="M15.05 5A5 5 0 0 1 19 8.95M15.05 1A9 9 0 0 1 23 8.94"/><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81.72A2 2 0 0 1 22 16.92z"/></>,
  'ear':           <><path d="M6 8.5a6.5 6.5 0 1 1 13 0c0 6-6 6-6 10a3.5 3.5 0 0 1-7 0v-1"/><path d="M6.5 12.5a3.5 3.5 0 1 1 7 0c0 1.6-1 2.4-1.5 3"/></>,
  'mail':          <><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/></>,
  'phone-incoming': <><polyline points="16 2 16 8 22 8"/><line x1="22" y1="2" x2="16" y2="8"/><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81.72A2 2 0 0 1 22 16.92z"/></>,
  'volume':        <><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/></>,
  'moon':          <><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/><path d="M18.5 2v3M17 3.5h3"/></>,
  'hourglass':     <><path d="M6 3h12"/><path d="M6 21h12"/><path d="M6 3c0 4 4 7 6 9 2-2 6-5 6-9"/><path d="M6 21c0-4 4-7 6-9 2 2 6 5 6 9"/></>,
  'butterfly':     <><path d="M12 7v11"/><path d="M12 9C10.5 5.5 7 4 4.5 5.5S3 10 6 11c-3 1-3.5 4.5-1 6s5.5-.5 7-3.5"/><path d="M12 9c1.5-3.5 5-5 7.5-3.5S21 10 18 11c3 1 3.5 4.5 1 6s-5.5-.5-7-3.5"/></>,
  'home':          <><path d="M3 12 12 3l9 9"/><path d="M5 10v10h14V10"/></>,
  'calendar':      <><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></>,
  'apple':         <path d="M16 4c.5 1.5-.5 3-2 3.5C12 8 11 7 11 5.5 12.5 4 14 3.5 16 4zM18.4 13.5c-.6 1.5-1.4 3-2.9 3-1.4 0-1.9-.8-3.5-.8-1.6 0-2.1.8-3.5.8-1.5 0-2.4-1.4-3.1-2.9C4 11 4.6 7.6 6.6 6.5c1.3-.7 2.5-.3 3.5 0 1 .3 1.4.3 2.4 0 1.1-.4 2.2-.9 3.6-.2-1.7 1.1-2.1 3.5-.4 4.8.5 1.1.8 1.6.7 2.4z" fill="currentColor"/>,
  'google':        <path d="M22 12.2c0-.8-.1-1.4-.2-2H12v3.9h5.6c-.2 1.3-1 2.3-2 3v2.5h3.3c1.9-1.8 3.1-4.4 3.1-7.4zM12 22c2.7 0 5-.9 6.7-2.4l-3.3-2.5c-.9.6-2 1-3.4 1-2.6 0-4.9-1.8-5.7-4.2H2.9v2.6C4.6 19.9 8 22 12 22zM6.3 13.9c-.2-.6-.3-1.3-.3-1.9s.1-1.3.3-1.9V7.5H2.9C2.3 8.9 2 10.4 2 12s.3 3.1.9 4.5l3.4-2.6zM12 5.9c1.5 0 2.8.5 3.9 1.5l2.9-2.9C17 2.9 14.7 2 12 2 8 2 4.6 4.1 2.9 7.5l3.4 2.6C7.1 7.7 9.4 5.9 12 5.9z" fill="currentColor"/>,
  'facebook':      <path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.45 2.89h-2.34v6.99A10 10 0 0 0 22 12z" fill="currentColor"/>,
};

// ─────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────
function Button({ variant = 'primary', size = 'md', children, leading, trailing, onClick, style = {}, fullWidth = false, disabled = false }) {
  const sizes = {
    sm: { h: 36, px: 16, fs: 14 },
    md: { h: 48, px: 22, fs: 15 },
    lg: { h: 56, px: 28, fs: 16 },
  }[size];
  const variants = {
    primary: {
      background: 'var(--liq-orange-500)',
      color: '#fff',
      boxShadow: 'var(--liq-shadow-cta)',
    },
    sunset: {
      background: 'var(--su-grad-sunset)',
      color: '#fff',
      boxShadow: 'var(--liq-shadow-violet)',
    },
    violet: {
      background: 'var(--liq-primary-500)',
      color: '#fff',
      boxShadow: 'var(--liq-shadow-violet)',
    },
    ghost: {
      background: 'transparent',
      color: 'var(--liq-fg)',
      border: '1px solid var(--liq-border)',
    },
    soft: {
      background: 'var(--liq-bg-raised)',
      color: 'var(--liq-primary-500)',
    },
    danger: {
      background: 'transparent',
      color: 'var(--liq-danger)',
      border: '1px solid rgba(251,50,59,0.35)',
    },
  }[variant];
  return (
    <button
      onClick={disabled ? undefined : onClick}
      style={{
        height: sizes.h,
        padding: `0 ${sizes.px}px`,
        borderRadius: 9999,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        fontFamily: 'var(--liq-font-sans)',
        fontWeight: 700,
        fontSize: sizes.fs,
        letterSpacing: 0,
        whiteSpace: 'nowrap',
        opacity: disabled ? 0.45 : 1,
        width: fullWidth ? '100%' : undefined,
        transition: 'transform 180ms cubic-bezier(.22,1,.36,1), filter 180ms',
        ...variants,
        ...style,
      }}
      onMouseDown={e => e.currentTarget.style.transform = 'scale(0.98)'}
      onMouseUp={e => e.currentTarget.style.transform = ''}
      onMouseLeave={e => e.currentTarget.style.transform = ''}
    >
      {leading}
      <span>{children}</span>
      {trailing}
    </button>
  );
}

// Round arrow CTA (Show Up's signature next button)
function NextButton({ label = 'Next', onClick, color = 'orange', size = 56 }) {
  const bg = color === 'orange' ? 'var(--liq-orange-500)' : 'var(--su-grad-sunset)';
  const sh = color === 'orange' ? 'var(--liq-shadow-cta)' : 'var(--liq-shadow-violet)';
  return (
    <button onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 14,
    }}>
      <span style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 17,
        color: 'var(--liq-fg)',
      }}>{label}</span>
      <span style={{
        width: size, height: size, borderRadius: 9999,
        background: bg, boxShadow: sh, color: '#fff',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name="arrow-right" size={22} stroke={2} />
      </span>
    </button>
  );
}

// Skip link — the ONE canonical "Skip for now" affordance.
// Every optional step uses this; never re-style it inline.
function SkipLink({ label = 'Skip for now', onClick }) {
  return (
    <button onClick={onClick} style={{
      fontFamily: 'var(--liq-font-sans)', fontSize: 13.5, fontWeight: 700,
      color: 'var(--liq-primary-500)',
      textDecoration: 'underline', textUnderlineOffset: 3,
      textDecorationThickness: 1.5,
      background: 'none', border: 'none', cursor: 'pointer', padding: 0,
    }}>{label}</button>
  );
}

// ─────────────────────────────────────────────────────────────
// Chip / Pill (intent, vibe, interest)
// ─────────────────────────────────────────────────────────────
function Chip({ children, selected = false, leading, onClick, tone = 'lavender', size = 'md' }) {
  const tones = {
    lavender: {
      idle: { background: '#fff', color: 'var(--liq-fg)', border: '1px solid var(--liq-border)' },
      sel:  { background: 'var(--liq-primary-500)', color: '#fff', border: '1px solid var(--liq-primary-500)' },
    },
    orange: {
      idle: { background: '#fff', color: 'var(--liq-fg)', border: '1px solid var(--liq-border)' },
      sel:  { background: 'rgba(254,104,57,0.10)', color: 'var(--liq-orange-500)', border: '1px solid var(--liq-orange-500)' },
    },
    soft: {
      idle: { background: 'var(--liq-bg-raised)', color: 'var(--liq-fg)', border: '1px solid transparent' },
      sel:  { background: 'var(--liq-primary-500)', color: '#fff', border: '1px solid var(--liq-primary-500)' },
    },
  }[tone];
  const sizes = { xs: { h: 26, fs: 11.5, px: 10, gap: 5 }, sm: { h: 30, fs: 13, px: 12, gap: 6 }, md: { h: 36, fs: 14, px: 14, gap: 8 } }[size];
  const s = selected ? tones.sel : tones.idle;
  return (
    <button onClick={onClick} style={{
      height: sizes.h, padding: `0 ${sizes.px}px`,
      borderRadius: 9999, display: 'inline-flex', alignItems: 'center',
      gap: sizes.gap, fontSize: sizes.fs, fontWeight: 600,
      fontFamily: 'var(--liq-font-sans)', whiteSpace: 'nowrap',
      transition: 'all 180ms cubic-bezier(.22,1,.36,1)',
      ...s,
    }}>
      {leading}
      <span>{children}</span>
    </button>
  );
}

// ─────────────────────────────────────────────────────────────
// Cards
// ─────────────────────────────────────────────────────────────
function Card({ children, style = {}, raised = false, onClick }) {
  return (
    <div onClick={onClick} style={{
      background: raised ? 'var(--liq-bg-raised)' : 'var(--liq-bg-elevated)',
      borderRadius: 20,
      border: '1px solid var(--liq-border-soft)',
      boxShadow: raised ? 'none' : 'var(--liq-shadow-md)',
      ...style,
    }}>
      {children}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Status bar / device frame (lightweight — we don't use the starter directly,
// it's heavyweight for our 30 frames. Render a slim iPhone-15 style shell.)
// ─────────────────────────────────────────────────────────────
function Phone({ children, height = 844, width = 390, time = '4:20', dark = false, label, scroll = true }) {
  // The wrapping div is what design_canvas measures; we use absolute children.
  return (
    <div className="su-app" style={{
      width, height, position: 'relative', overflow: height === 'auto' ? 'visible' : 'hidden',
      background: 'var(--liq-bg)',
      display: 'flex', flexDirection: 'column',
    }}>
      <StatusBar time={time} dark={dark} />
      <div className="su-noscroll" style={{
        flex: height === 'auto' ? '1 0 auto' : 1, position: 'relative',
        overflow: scroll && height !== 'auto' ? 'auto' : 'visible',
      }}>
        {children}
      </div>
      <HomeIndicator dark={dark} />
    </div>
  );
}

function StatusBar({ time = '4:20', dark = false }) {
  const c = dark ? '#fff' : '#1D1129';
  return (
    <div style={{
      height: 54, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0 30px', flex: 'none', position: 'relative', zIndex: 5,
    }}>
      <span style={{ fontFamily: '-apple-system, "SF Pro", system-ui', fontWeight: 600, fontSize: 17, color: c }}>{time}</span>
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <svg width="18" height="11" viewBox="0 0 18 11"><rect x="0" y="6" width="3" height="4" rx="0.6" fill={c}/><rect x="5" y="4" width="3" height="6" rx="0.6" fill={c}/><rect x="10" y="2" width="3" height="8" rx="0.6" fill={c}/><rect x="15" y="0" width="3" height="10" rx="0.6" fill={c}/></svg>
        <svg width="16" height="11" viewBox="0 0 16 11"><path d="M8 3.5C9.9 3.5 11.6 4.2 12.9 5.4L14 4.3C12.4 2.8 10.3 1.8 8 1.8C5.7 1.8 3.6 2.8 2 4.3L3.1 5.4C4.4 4.2 6.1 3.5 8 3.5Z" fill={c}/><path d="M8 6.5C8.9 6.5 9.8 6.8 10.4 7.4L11.5 6.3C10.5 5.4 9.3 4.9 8 4.9C6.7 4.9 5.5 5.4 4.5 6.3L5.6 7.4C6.2 6.8 7.1 6.5 8 6.5Z" fill={c}/><circle cx="8" cy="9.5" r="1" fill={c}/></svg>
        <svg width="25" height="12" viewBox="0 0 25 12"><rect x="0.5" y="0.5" width="21" height="11" rx="3" stroke={c} strokeOpacity="0.4" fill="none"/><rect x="2" y="2" width="18" height="8" rx="1.5" fill={c}/><path d="M23 4v4c0.6-0.2 1.2-0.9 1.2-2 0-1.1-0.6-1.8-1.2-2z" fill={c} fillOpacity="0.4"/></svg>
      </div>
    </div>
  );
}

function HomeIndicator({ dark = false }) {
  return (
    <div style={{
      height: 28, flex: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'transparent', position: 'relative', zIndex: 5,
    }}>
      <div style={{ width: 134, height: 5, borderRadius: 999, background: dark ? '#fff' : '#1D1129', opacity: 0.85 }} />
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// App header (page-level title + back / close)
// ─────────────────────────────────────────────────────────────
function AppHeader({ title, leading = 'back', trailing, onBack, dense = false }) {
  return (
    <div style={{
      height: dense ? 44 : 52, padding: '0 16px', display: 'flex',
      alignItems: 'center', gap: 8,
    }}>
      <div style={{ width: 36, display: 'flex', justifyContent: 'flex-start' }}>
        {leading === 'back' && (
          <button onClick={onBack} style={{
            width: 36, height: 36, borderRadius: 18,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="chevron-left" size={24} stroke={2} />
          </button>
        )}
        {leading === 'close' && (
          <button onClick={onBack} style={{
            width: 36, height: 36, borderRadius: 18,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon name="x" size={22} stroke={2} />
          </button>
        )}
      </div>
      <div style={{ flex: 1, textAlign: 'center', fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 16, color: 'var(--liq-fg)' }}>
        {title}
      </div>
      <div style={{ width: 36, display: 'flex', justifyContent: 'flex-end' }}>{trailing}</div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Tab bar (Show Up's bottom nav)
//
// Three tabs — Search, Instant, Settings. Always visible on the
// Date Search and Match screens; on Match the Search + Instant
// tabs are disabled while the user has an active date. Tapping a
// disabled tab pops a short hint above the bar explaining why
// browsing is locked.
// ─────────────────────────────────────────────────────────────
const TABS = [
  { id: 'search',   icon: 'search',   label: 'Search' },
  { id: 'instant',  icon: 'zap',      label: 'Instant' },
  { id: 'settings', icon: 'settings', label: 'Settings' },
];
function TabBar({ active = 'search', onTab, disabled = [], disabledHint = '', sticky = true }) {
  const [hint, setHint] = useState(null);
  const timerRef = useRef(null);
  function tap(id) {
    if (disabled.indexOf(id) !== -1) {
      setHint(disabledHint || 'Not available right now.');
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => setHint(null), 3200);
      return;
    }
    onTab && onTab(id);
  }
  return (
    <div style={{
      position: sticky ? 'sticky' : 'static', bottom: sticky ? 0 : 'auto',
      left: 0, right: 0, zIndex: 4,
    }}>
      {/* Hint toast — appears above the bar when a disabled tab is tapped */}
      <div style={{
        position: 'absolute', left: 14, right: 14, bottom: 'calc(100% + 10px)',
        display: 'flex', justifyContent: 'center', pointerEvents: 'none',
      }}>
        <div style={{
          maxWidth: 320,
          padding: '11px 14px',
          borderRadius: 14,
          background: 'var(--liq-fg)',
          color: '#fff',
          fontFamily: 'var(--liq-font-sans)', fontSize: 13, fontWeight: 500,
          lineHeight: 1.4, letterSpacing: 0.01,
          boxShadow: '0 12px 28px rgba(20,12,28,0.22)',
          display: 'flex', alignItems: 'flex-start', gap: 10,
          opacity: hint ? 1 : 0,
          transform: `translateY(${hint ? 0 : 8}px)`,
          transition: 'opacity 200ms, transform 200ms',
        }}>
          <Icon name="lock" size={14} stroke={2.4}
            style={{ marginTop: 1, color: 'var(--liq-orange-400)' }}/>
          <span style={{ flex: 1, textWrap: 'pretty' }}>{hint || '\u00A0'}</span>
        </div>
      </div>

      <div style={{
        background: 'rgba(255,251,247,0.92)', backdropFilter: 'blur(20px)',
        borderTop: '1px solid var(--liq-border-soft)',
        padding: '8px 8px 0', display: 'flex', justifyContent: 'space-around',
      }}>
        {TABS.map(t => {
          const sel = t.id === active;
          const dis = disabled.indexOf(t.id) !== -1;
          const color = sel
            ? 'var(--liq-primary-500)'
            : dis ? 'var(--liq-fg-faint)' : 'var(--liq-fg-subtle)';
          return (
            <button key={t.id} onClick={() => tap(t.id)} style={{
              flex: 1, padding: '8px 0', display: 'flex', flexDirection: 'column',
              alignItems: 'center', gap: 3,
              color, position: 'relative',
            }}>
              <span style={{
                position: 'relative',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={t.icon} size={24} stroke={sel ? 2.2 : 1.7}/>
                {dis && (
                  <span style={{
                    position: 'absolute', right: -7, bottom: -3,
                    width: 14, height: 14, borderRadius: 9999,
                    background: 'var(--liq-bg)',
                    border: '1.5px solid var(--liq-fg-faint)',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    color: 'var(--liq-fg-muted)',
                  }}>
                    <Icon name="lock" size={8} stroke={3}/>
                  </span>
                )}
              </span>
              <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: 0.2 }}>{t.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Avatar — painterly gradient placeholder with initial (Show Up vibe)
// ─────────────────────────────────────────────────────────────
const AVATAR_GRADIENTS = {
  Leonie: 'linear-gradient(155deg, #FCC8A7 0%, #E89B7A 40%, #B86B5C 100%)',
  Sofia:  'linear-gradient(155deg, #EAD9F4 0%, #C8A6E8 40%, #8B5CF6 100%)',
  Maya:   'linear-gradient(155deg, #DCE9D0 0%, #A5C99F 50%, #5B8C66 100%)',
  Marcus: 'linear-gradient(155deg, #DCE3F1 0%, #A8B9D8 50%, #5B6E92 100%)',
  Emma:   'linear-gradient(155deg, #FCDCDC 0%, #E69CB5 50%, #B85B7C 100%)',
  Tom:    'linear-gradient(155deg, #EAD7C5 0%, #C8A286 50%, #8B6249 100%)',
  Anna:   'linear-gradient(155deg, #F1D9EA 0%, #D8A6CC 40%, #8B5C8B 100%)',
  Jonas:  'linear-gradient(155deg, #D6E8E6 0%, #99C7C3 50%, #4D8F88 100%)',
  Liv:    'linear-gradient(155deg, #FFE2C5 0%, #FFB58A 40%, #E07A4C 100%)',
};
function avatarGrad(name) {
  return AVATAR_GRADIENTS[name] || 'linear-gradient(155deg, #E9DCFA 0%, #B89DD8 50%, #6B4D8F 100%)';
}

// ─────────────────────────────────────────────────────────────
// Real-photo map — natural, unposed portraits of the women whose
// profiles appear in the prototype. Multiple URLs per name = a
// gallery (hero, secondary photos, etc — addressed by photoIndex).
// Sourced from Unsplash (free license, hot-linkable).
// ─────────────────────────────────────────────────────────────
const _U = (id) => `https://images.unsplash.com/photo-${id}?w=800&auto=format&fit=crop&q=80`;
// URL table keyed by the same id used in <meta name="ext-resource-dependency">
// At runtime, photoFor() prefers window.__resources[id] (set by the standalone
// bundler) and falls back to the live Unsplash URL during dev.
const PHOTO_URLS = {
  ph_leonie_0: _U('1494790108377-be9c29b29330'),
  ph_leonie_1: _U('1531123897727-8f129e1688ce'),
  ph_leonie_2: _U('1517365830460-955ce3ccd263'),
  ph_sofia_0:  _U('1500917293891-ef795e70e1f6'),
  ph_sofia_1:  _U('1488426862026-3ee34a7d66df'),
  ph_sofia_2:  _U('1502823403499-6ccfcf4fb453'),
  ph_maya_0:   _U('1488716820095-cbe80883c496'),
  ph_maya_1:   _U('1485178575877-1a13bf489dfe'),
  ph_anna_0:   _U('1487412720507-e7ab37603c6f'),
  ph_anna_1:   _U('1529626455594-4ff0802cfb7e'),
  ph_emma_0:   _U('1508214751196-bcfd4ca60f91'),
  ph_emma_1:   _U('1499887142886-791eca5918cd'),
  ph_liv_0:    _U('1524504388940-b1c1722653e1'),
  ph_liv_1:    _U('1531746020798-e6953c6e8e04'),
  ph_you_0:    _U('1517841905240-472988babdf9'),
  ph_you_1:    _U('1438761681033-6461ffad8d80'),
  // Male portraits for Jonas, Felix, Marcus, Tom and the "Leo" self avatar.
  // Not pre-bundled into assets/photos/ — they resolve at runtime against
  // the Unsplash fallback URL (no entry in app.jsx's __resources map).
  ph_jonas_0:  _U('1500648767791-00dcc994a43e'),
  ph_jonas_1:  _U('1507003211169-0a1dd7228f2d'),
  ph_felix_0:  _U('1463453091185-61582044d556'),
  ph_felix_1:  _U('1492562080023-ab3db95bfbce'),
  ph_marcus_0: _U('1539571696357-5a69c17a67c6'),
  ph_marcus_1: _U('1504593811423-6dd665756598'),
  ph_tom_0:    _U('1519085360753-af0119f7cbe7'),
  ph_tom_1:    _U('1531891437562-4301cf35b7e4'),
  ph_leo_0:    _U('1500648767791-00dcc994a43e'),
  ph_leo_1:    _U('1507003211169-0a1dd7228f2d'),
};
const PHOTOS = {
  Leonie: ['ph_leonie_0', 'ph_leonie_1', 'ph_leonie_2'],
  Sofia:  ['ph_sofia_0',  'ph_sofia_1',  'ph_sofia_2'],
  Maya:   ['ph_maya_0',   'ph_maya_1'],
  Anna:   ['ph_anna_0',   'ph_anna_1'],
  Emma:   ['ph_emma_0',   'ph_emma_1'],
  Liv:    ['ph_liv_0',    'ph_liv_1'],
  You:    ['ph_you_0',    'ph_you_1'],
  Jonas:  ['ph_jonas_0',  'ph_jonas_1'],
  Felix:  ['ph_felix_0',  'ph_felix_1'],
  Marcus: ['ph_marcus_0', 'ph_marcus_1'],
  Tom:    ['ph_tom_0',    'ph_tom_1'],
  Leo:    ['ph_leo_0',    'ph_leo_1'],
};
function photoFor(name, idx = 0) {
  const arr = PHOTOS[name];
  if (!arr) return null;
  const id = arr[idx % arr.length];
  if (!id) return null;
  return (typeof window !== 'undefined' && window.__resources && window.__resources[id]) || PHOTO_URLS[id];
}
function Avatar({ name = '?', size = 56, rounded = 'circle', initial, photoIndex = 0 }) {
  const r = rounded === 'circle' ? 9999 : (rounded === 'square' ? 16 : rounded);
  const init = initial || (name[0] || '?');
  const url = photoFor(name, photoIndex);
  return (
    <div style={{
      width: size, height: size, borderRadius: r,
      background: avatarGrad(name),
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: 'var(--liq-font-serif)', fontWeight: 600,
      fontSize: size * 0.42, color: 'rgba(255,255,255,0.92)',
      flex: 'none', position: 'relative', overflow: 'hidden',
    }}>
      {url && (
        <img src={url} alt={name} style={{
          position: 'absolute', inset: 0, width: '100%', height: '100%',
          objectFit: 'cover', display: 'block',
        }}/>
      )}
      {/* painterly noise overlay (also makes photos feel tonal) */}
      <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(circle at 30% 25%, rgba(255,255,255,0.18) 0%, transparent 60%)' }}/>
      {!url && init}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Big profile photo (painterly placeholder)
// ─────────────────────────────────────────────────────────────
function PortraitPlaceholder({ name = 'Leonie', height = 360, gradient, children, radius = 28, label = true, style = {}, photo, photoIndex = 0, objectPosition = 'center' }) {
  const url = photo || photoFor(name, photoIndex);
  return (
    <div style={{
      height, borderRadius: radius, overflow: 'hidden', position: 'relative',
      background: gradient || avatarGrad(name),
      ...style,
    }}>
      {url && (
        <img src={url} alt={name} style={{
          position: 'absolute', inset: 0, width: '100%', height: '100%',
          objectFit: 'cover', objectPosition, display: 'block',
        }}/>
      )}
      {/* layered painterly highlights — softened to a subtle wash when a photo is loaded */}
      <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(60% 40% at 35% 28%, rgba(255,255,255,0.30), transparent 65%)', opacity: url ? 0.35 : 1 }}/>
      <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(50% 50% at 70% 75%, rgba(0,0,0,0.18), transparent 70%)', opacity: url ? 0.4 : 1 }}/>
      {label && !url && (
        <div style={{
          position: 'absolute', top: 14, left: 16,
          padding: '4px 10px', borderRadius: 9999, background: 'rgba(255,255,255,0.85)',
          fontSize: 11, fontWeight: 600, color: 'var(--liq-fg)', letterSpacing: 0.2,
        }}>Photo · {name}</div>
      )}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        height: '40%', background: 'linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.42) 100%)',
      }}/>
      {children}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Eyebrow tag (lavender pill with orange dot)
// ─────────────────────────────────────────────────────────────
function Eyebrow({ children, color = 'lavender' }) {
  const tones = {
    lavender: { bg: 'rgba(167,139,250,0.16)', fg: 'var(--liq-primary-500)', dot: 'var(--liq-orange-500)' },
    orange:   { bg: 'rgba(254,104,57,0.12)', fg: 'var(--liq-orange-500)', dot: 'var(--liq-orange-500)' },
    mint:     { bg: 'rgba(0,171,85,0.12)', fg: '#0A7A47', dot: '#0A7A47' },
  }[color];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '5px 10px', borderRadius: 9999, background: tones.bg, color: tones.fg,
      fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: 11,
      letterSpacing: 0.08, textTransform: 'uppercase',
    }}>
      <span style={{ width: 5, height: 5, borderRadius: 9999, background: tones.dot }}/>
      {children}
    </span>
  );
}

// ─────────────────────────────────────────────────────────────
// Time slot button (Check-In)
// ─────────────────────────────────────────────────────────────
// states: 'idle' | 'preferred' | 'backup' | 'past'
function TimeSlot({ label, state = 'idle', onClick }) {
  const styles = {
    idle:      { background: '#fff', color: 'var(--liq-fg)', border: '1px solid var(--liq-border)' },
    preferred: { background: 'var(--liq-orange-500)', color: '#fff', border: '1px solid var(--liq-orange-500)', boxShadow: 'var(--liq-shadow-cta)' },
    backup:    { background: 'rgba(254,104,57,0.12)', color: 'var(--liq-orange-500)', border: '1px solid var(--liq-orange-500)' },
    past:      { background: 'transparent', color: 'var(--liq-fg-faint)', border: '1px solid var(--liq-border-soft)' },
  }[state];
  return (
    <button onClick={onClick} style={{
      height: 44, minWidth: 64, padding: '0 12px', borderRadius: 12,
      fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14,
      transition: 'all 180ms', ...styles,
    }}>{label}</button>
  );
}

// ─────────────────────────────────────────────────────────────
// Progress bar (onboarding / check-in steps)
// ─────────────────────────────────────────────────────────────
function StepProgress({ steps = 5, current = 1, style = {} }) {
  return (
    <div style={{ display: 'flex', gap: 6, ...style }}>
      {Array.from({ length: steps }).map((_, i) => (
        <div key={i} style={{
          flex: 1, height: 5, borderRadius: 999,
          background: i < current ? 'var(--liq-primary-500)' : 'var(--liq-border)',
          transition: 'background 280ms',
        }}/>
      ))}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Show-up Rate — tone resolution
//   ≥ 80  → green   (reliable)
//   50–79 → orange  (watch)
//   < 50  → red     (at risk)
// ─────────────────────────────────────────────────────────────
function showUpTone(value) {
  if (value >= 80) return {
    name: 'good',
    stroke: '#00AB55', fg: '#0A7A47', bg: 'rgba(0,171,85,0.14)',
    softBg: 'rgba(0,171,85,0.10)', softFg: '#0A7A47',
    pillBg: 'rgba(20,28,24,0.78)', pillFg: '#3DE08A',
  };
  if (value >= 50) return {
    name: 'watch',
    stroke: '#FE6839', fg: '#C44A22', bg: 'rgba(254,104,57,0.16)',
    softBg: 'rgba(254,104,57,0.10)', softFg: '#C44A22',
    pillBg: 'rgba(34,22,16,0.78)', pillFg: '#FFA268',
  };
  return {
    name: 'risk',
    stroke: '#FB323B', fg: '#B71F26', bg: 'rgba(251,50,59,0.16)',
    softBg: 'rgba(251,50,59,0.10)', softFg: '#B71F26',
    pillBg: 'rgba(36,16,18,0.80)', pillFg: '#FF6E73',
  };
}

// ─────────────────────────────────────────────────────────────
// Score donut (Show-up Rate ring) — color-coded by tone
// ─────────────────────────────────────────────────────────────
function ScoreRing({ value = 94, size = 64, label, stroke = 6, tone }) {
  const t = tone || showUpTone(value);
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const off = c - (value / 100) * c;
  return (
    <div style={{ width: size, height: size, position: 'relative', flex: 'none' }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size/2} cy={size/2} r={r} stroke="var(--liq-border)" strokeWidth={stroke} fill="none"/>
        <circle cx={size/2} cy={size/2} r={r}
          stroke={t.stroke} strokeWidth={stroke} fill="none"
          strokeDasharray={c} strokeDashoffset={off} strokeLinecap="round"
          style={{ transition: 'stroke-dashoffset 600ms' }}/>
      </svg>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', alignItems: 'center',
        justifyContent: 'center', flexDirection: 'column', gap: 0,
      }}>
        <span style={{ fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: size * 0.32, color: 'var(--liq-fg)', lineHeight: 1 }}>{value}</span>
        {label && <span style={{ fontSize: 9, color: 'var(--liq-fg-subtle)', fontWeight: 600 }}>{label}</span>}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Show-up Rate Pill — small horizontal pill: ring + "NN% Show-up"
// variants:
//   'soft'  — light tinted background (for cards / inline rows)
//   'dark'  — translucent dark capsule (for image overlays — premium grid)
//   'plain' — no background, just colored text + ring (for tight rows)
// ─────────────────────────────────────────────────────────────
function ShowUpPill({ value = 94, variant = 'soft', size = 'md' }) {
  const t = showUpTone(value);
  const dims = size === 'xs'
    ? { ring: 18, stroke: 2.2, fs: 11, pad: '4px 8px 4px 5px', gap: 6 }
    : size === 'sm'
    ? { ring: 22, stroke: 2.6, fs: 12, pad: '5px 10px 5px 6px', gap: 7 }
    : { ring: 28, stroke: 3,   fs: 13, pad: '6px 12px 6px 6px', gap: 8 };

  const palette = variant === 'dark'
    ? { bg: t.pillBg, fg: t.pillFg, border: 'transparent' }
    : variant === 'plain'
    ? { bg: 'transparent', fg: t.fg, border: 'transparent' }
    : { bg: t.softBg, fg: t.fg, border: 'transparent' };

  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: dims.gap,
      padding: dims.pad, borderRadius: 9999,
      background: palette.bg, color: palette.fg,
      backdropFilter: variant === 'dark' ? 'blur(8px)' : undefined,
      fontFamily: 'var(--liq-font-sans)', fontWeight: 700, fontSize: dims.fs,
      letterSpacing: 0.02, lineHeight: 1, whiteSpace: 'nowrap',
    }}>
      <ScoreRing value={value} size={dims.ring} stroke={dims.stroke} tone={t}/>
      <span><span style={{ fontWeight: 800 }}>{value}%</span> Show-up</span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Section heading (eyebrow + title + sub)
// ─────────────────────────────────────────────────────────────
function SectionHead({ eyebrow, title, sub, align = 'left' }) {
  return (
    <div style={{ textAlign: align, display: 'flex', flexDirection: 'column', gap: 8 }}>
      {eyebrow && <div><Eyebrow>{eyebrow}</Eyebrow></div>}
      {title && (
        <h2 className="su-underlined" style={{
          fontFamily: 'var(--liq-font-serif)', fontWeight: 700, fontSize: 28,
          letterSpacing: '-0.01em', lineHeight: 1.15, color: 'var(--liq-fg)',
          textWrap: 'pretty', margin: 0,
        }}>{title}</h2>
      )}
      {sub && <p style={{
        fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 15,
        lineHeight: 1.5, color: 'var(--liq-neutral-200)', textWrap: 'pretty', margin: 0,
      }}>{sub}</p>}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Input field
// ─────────────────────────────────────────────────────────────
function Field({ label, hint, value, onChange, placeholder, type = 'text', error, success }) {
  const [focus, setFocus] = useState(false);
  const borderColor = error ? 'var(--liq-danger)' :
                      success ? 'var(--liq-success)' :
                      focus ? 'var(--liq-primary-500)' : 'var(--liq-border)';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {label && <label style={{ fontFamily: 'var(--liq-font-sans)', fontWeight: 600, fontSize: 14, color: 'var(--liq-fg)' }}>{label}</label>}
      <div style={{
        display: 'flex', alignItems: 'center', height: 56, padding: '0 18px',
        borderRadius: 14, background: '#fff',
        border: `1.5px solid ${borderColor}`, transition: 'border-color 180ms',
      }}>
        <input
          value={value || ''} onChange={e => onChange && onChange(e.target.value)}
          onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
          placeholder={placeholder} type={type}
          style={{
            flex: 1, border: 'none', outline: 'none', background: 'transparent',
            fontFamily: 'var(--liq-font-sans)', fontWeight: 500, fontSize: 16, color: 'var(--liq-fg)',
          }}
        />
        {success && <Icon name="check" size={20} style={{ color: 'var(--liq-success)' }}/>}
      </div>
      {hint && !error && <span style={{ fontSize: 13, color: 'var(--liq-fg-subtle)' }}>{hint}</span>}
      {error && <span style={{ fontSize: 13, color: 'var(--liq-danger)' }}>{error}</span>}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Ambient atmosphere — Show Up's signature warm wash that lives
// INSIDE every screen. A sunset-orange glow drifts in from the
// top-right and a violet glow from the bottom-left, so the canvas
// is never a flat sheet of paper.
//
// ONE rule, TWO intensities (identical geometry — only the glow
// strength changes — so the whole product feels like one continuous
// space rather than a set of unrelated screens):
//   variant="hero" — expressive / emotional screens (prompts, photos,
//                    embrace, welcome). Richer, more present glow.
//   variant="form" — input / verification / data-entry screens (email,
//                    code, the "share some details" flow). Soft, quiet wash.
//
// Drop it as the FIRST child inside a .su-app frame. It clips to the
// frame, sits behind content (zIndex 0) and never eats pointer events.
// ─────────────────────────────────────────────────────────────
const ATMOSPHERE = {
  hero: { orange: 0.26, violet: 0.22 },
  form: { orange: 0.13, violet: 0.11 }
};
function Atmosphere({ variant = 'hero', style = {} }) {
  const t = ATMOSPHERE[variant] || ATMOSPHERE.hero;
  return (
    <div aria-hidden="true" style={{
      position: 'absolute', inset: 0, zIndex: 0,
      overflow: 'hidden', pointerEvents: 'none', ...style
    }}>
      <div style={{
        position: 'absolute', top: '-18%', right: '-25%', width: 460, height: 460,
        borderRadius: '50%',
        background: `radial-gradient(circle, rgba(254,104,57,${t.orange}) 0%, rgba(254,104,57,0) 65%)`,
        filter: 'blur(12px)'
      }} />
      <div style={{
        position: 'absolute', bottom: '-16%', left: '-26%', width: 460, height: 460,
        borderRadius: '50%',
        background: `radial-gradient(circle, rgba(129,42,236,${t.violet}) 0%, rgba(129,42,236,0) 65%)`,
        filter: 'blur(12px)'
      }} />
    </div>);

}

// ─────────────────────────────────────────────────────────────
// ProfileVisibility — the ONE profile-visibility control
//
// Used on every profile-creation step that asks for an attribute we
// may or may not publish. Replaces the two divergent checkbox rows
// (HideAgeOptOut, PrivacyToggle).
//
// Design rules, deliberate:
//   - Checkbox on the LEFT of a fixed label ("Don't display on my
//     profile"), so one control with one wording appears on every step.
//   - Default OFF (`visible` defaults true → box unchecked): the user
//     actively opts out of displaying the answer.
//   - Switch on the LEFT and the hit area capped to the switch + label
//     (never extending over the bottom-right Continue button's x-range),
//     with ≥24px of vertical clearance, so a thumb reaching for one
//     cannot silently flip a privacy setting.
//   - Sits in a hairline-topped band pinned in the viewport directly
//     above the footer, even when the answer list scrolls.
//   - `note` carries the standing reassurance ("still used for
//     matching") so screens stop inventing their own wording.
// ─────────────────────────────────────────────────────────────
function ProfileVisibility({
  visible = true,
  onToggle,
  label = "Don't display on my profile",
  note = null,
}) {
  return (
    <div style={{ borderTop: '1px solid var(--liq-border-soft)', paddingBottom: note ? 8 : 0 }}>
    <button
      type="button"
      onClick={onToggle}
      role="checkbox"
      aria-checked={!visible}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 14,
        textAlign: 'left', whiteSpace: 'nowrap',
        minHeight: 56, padding: '10px 0',
        background: 'transparent',
      }}>
      {/* Checkbox — same 22px square as the answer rows, checked = hidden */}
      <span style={{
        flex: 'none', width: 22, height: 22, borderRadius: 6,
        background: visible ? '#FFFFFF' : 'var(--liq-primary-500)',
        border: visible ? '1.5px solid var(--liq-border)' : '1.5px solid var(--liq-primary-500)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'background 180ms, border-color 180ms',
      }}>
        {!visible && (
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
            stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="5 12 10 17 19 7"/>
          </svg>
        )}
      </span>

      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{
          display: 'flex', alignItems: 'center', gap: 6,
          fontFamily: 'var(--liq-font-sans)', fontSize: 14,
          fontWeight: 700, lineHeight: 1.3, color: 'var(--liq-fg)',
        }}>
          <Icon name="eye-off" size={14} stroke={2.1}
            style={{ color: visible ? 'var(--liq-fg-subtle)' : 'var(--liq-primary-500)', flex: 'none' }}/>
          {label}
        </span>
      </span>
    </button>
    {/* Note sits OUTSIDE the tappable box: full width (so it never wraps
        into a tall column) and not part of the hit area. */}
    {note && (
      <div style={{
        fontFamily: 'var(--liq-font-sans)', fontSize: 11.5,
        fontWeight: 500, lineHeight: 1.35, color: 'var(--liq-fg-subtle)',
        marginTop: -4,
      }}>
        {note}
      </div>
    )}
    </div>
  );
}

// Expose everything to other Babel bundles
Object.assign(window, {
  Wordmark, Icon, Button, NextButton, SkipLink, Chip, Card, Phone, AppHeader, TabBar,
  Avatar, PortraitPlaceholder, Eyebrow, TimeSlot, StepProgress, ScoreRing,
  ShowUpPill, showUpTone, StatusBar, HomeIndicator,
  SectionHead, Field, avatarGrad, photoFor, PHOTOS,
  Atmosphere, ProfileVisibility,
});
