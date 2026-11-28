import { NotificationType } from '../util/preferences';

/**
 * EN/DE message catalog (SHOWUP-68/71). A small typed dictionary instead of a full i18n framework —
 * every message type has an English and German template that produces a `{ title, body }`. For push,
 * `title`/`body` are the notification's title and text; for email, `title` is the subject and `body`
 * the email content. Adding a language = add a locale key; adding a message = add a type entry.
 */
export type Locale = 'en' | 'de';

export interface RenderedMessage {
  title: string;
  body: string;
}

export type MessageParams = Record<string, string>;
type Template = (p: MessageParams) => RenderedMessage;

const CATALOG: Record<NotificationType, Record<Locale, Template>> = {
  date_reminder: {
    en: (p) => ({
      title: 'Your date is coming up',
      body: `Reminder: your date is at ${p.time}${p.venue ? ` at ${p.venue}` : ''}. See you there!`,
    }),
    de: (p) => ({
      title: 'Dein Date steht bald an',
      body: `Erinnerung: Dein Date ist um ${p.time}${p.venue ? ` im ${p.venue}` : ''}. Bis gleich!`,
    }),
  },
  // Interim copy (Date 03, R6): the final copy lands later. The name is the person who arrived.
  date_partner_arrived: {
    // No name on the profile: neutral copy rather than " has arrived".
    en: (p) =>
      p.name
        ? {
            title: `${p.name} has arrived`,
            body: `${p.name} has arrived at the meeting location.`,
          }
        : {
            title: 'Your date has arrived',
            body: 'Your date has arrived at the meeting location.',
          },
    de: (p) =>
      p.name
        ? {
            title: `${p.name} ist da`,
            body: `${p.name} ist am Treffpunkt angekommen.`,
          }
        : {
            title: 'Dein Date ist da',
            body: 'Dein Date ist am Treffpunkt angekommen.',
          },
  },
  // Interim copy (Date 05, R10): all notification copy is written at the end of the project. Never a
  // place, a time other than the deadline, or anything else about the suggestion: only that there is one.
  date_change_suggested: {
    en: (p) => ({
      title: p.name
        ? `${p.name} suggested a change`
        : 'Your date suggested a change',
      body: `Answer in Show Up before ${p.deadline}.`,
    }),
    de: (p) => ({
      title: p.name
        ? `${p.name} schlägt eine Änderung vor`
        : 'Dein Date schlägt eine Änderung vor',
      body: `Antworte in Show Up bis ${p.deadline}.`,
    }),
  },
  date_change_agreed: {
    en: (p) => ({
      title: p.name
        ? `${p.name} agreed to your change`
        : 'Your date agreed to your change',
      body: "It's now binding for you both.",
    }),
    de: (p) => ({
      title: p.name
        ? `${p.name} hat deiner Änderung zugestimmt`
        : 'Dein Date hat deiner Änderung zugestimmt',
      body: 'Sie gilt jetzt für euch beide.',
    }),
  },
  date_change_expired: {
    en: () => ({
      title: "Your change wasn't agreed",
      body: 'The original time and place stay.',
    }),
    de: () => ({
      title: 'Deine Änderung wurde nicht bestätigt',
      body: 'Die ursprüngliche Zeit und der Ort bleiben.',
    }),
  },
  // TEMPORARY placeholder copy (Date 07, ticket Open item 1): all push copy is written at the end of the
  // project, in one pass. The canceller's first name only; never a reason, a place or a time.
  date_cancelled: {
    en: (p) => ({
      title: p.name
        ? `${p.name} cancelled your date`
        : 'Your date was cancelled',
      body: 'Open Show Up to see what happens next.',
    }),
    de: (p) => ({
      title: p.name
        ? `${p.name} hat euer Date abgesagt`
        : 'Dein Date wurde abgesagt',
      body: 'Öffne Show Up, um zu sehen, wie es weitergeht.',
    }),
  },
  // TEMPORARY placeholder copy (Date 08, Open item 2): all push copy is written at the end of the
  // project, in one pass. The ender's first name only; never a reason, a place or a time.
  date_partner_ended: {
    en: (p) => ({
      title: p.name ? `${p.name} ended your date` : 'Your date was ended',
      body: 'Open Show Up to see what happens next.',
    }),
    de: (p) => ({
      title: p.name
        ? `${p.name} hat euer Date beendet`
        : 'Dein Date wurde beendet',
      body: 'Öffne Show Up, um zu sehen, wie es weitergeht.',
    }),
  },
  check_in_expiry: {
    // Deliberately discreet — a check-in is tied to the user's real-world location (privacy/safety).
    en: () => ({
      title: 'Still up for meeting?',
      body: 'Your availability is about to end. Check in again to stay visible.',
    }),
    de: () => ({
      title: 'Noch bereit für ein Treffen?',
      body: 'Deine Verfügbarkeit endet bald. Checke erneut ein, um sichtbar zu bleiben.',
    }),
  },
  new_match: {
    en: () => ({
      title: "It's a match!",
      body: 'You have a new match on ShowUp. Open the app to say hello.',
    }),
    de: () => ({
      title: 'Es hat gematcht!',
      body: 'Du hast ein neues Match bei ShowUp. Öffne die App und sag Hallo.',
    }),
  },
  email_verification: {
    en: (p) => ({
      title: 'Confirm your email',
      body: `Your ShowUp email verification code is ${p.code}.`,
    }),
    de: (p) => ({
      title: 'Bestätige deine E-Mail',
      body: `Dein ShowUp-Bestätigungscode lautet ${p.code}.`,
    }),
  },
  password_reset: {
    en: (p) => ({
      title: 'Reset your password',
      body: `Use this code to reset your ShowUp password: ${p.code}.`,
    }),
    de: (p) => ({
      title: 'Passwort zurücksetzen',
      body: `Verwende diesen Code, um dein ShowUp-Passwort zurückzusetzen: ${p.code}.`,
    }),
  },
  account_closure: {
    en: () => ({
      title: 'Your account is being closed',
      body: 'We have received your request to close your ShowUp account. This can take up to 30 days.',
    }),
    de: () => ({
      title: 'Dein Konto wird geschlossen',
      body: 'Wir haben deine Anfrage zur Schließung deines ShowUp-Kontos erhalten. Dies kann bis zu 30 Tage dauern.',
    }),
  },
  security_alert: {
    en: (p) => ({
      title: 'Security alert',
      body: `A security-relevant change was made to your account${p.detail ? `: ${p.detail}` : ''}.`,
    }),
    de: (p) => ({
      title: 'Sicherheitshinweis',
      body: `An deinem Konto wurde eine sicherheitsrelevante Änderung vorgenommen${p.detail ? `: ${p.detail}` : ''}.`,
    }),
  },
  safety_alert: {
    en: (p) => ({
      title: 'Safety notice',
      body:
        p.detail ??
        'Please review an important safety notice about your recent activity.',
    }),
    de: (p) => ({
      title: 'Sicherheitsmitteilung',
      body:
        p.detail ??
        'Bitte lies eine wichtige Sicherheitsmitteilung zu deiner letzten Aktivität.',
    }),
  },
  marketing_generic: {
    en: (p) => ({
      title: p.title ?? 'News from ShowUp',
      body: p.body ?? 'See what’s new on ShowUp.',
    }),
    de: (p) => ({
      title: p.title ?? 'Neues von ShowUp',
      body: p.body ?? 'Entdecke, was es Neues bei ShowUp gibt.',
    }),
  },
};

/**
 * Render a message for a type + locale, interpolating params. Unknown locales fall back to English
 * so a mislabelled user always gets a readable message. Unknown types throw — a missing template is
 * a programming error, not something to silently swallow.
 */
export function renderMessage(
  type: NotificationType,
  locale: Locale,
  params: MessageParams = {},
): RenderedMessage {
  const byLocale = CATALOG[type];
  if (!byLocale) {
    throw new Error(`No message template for notification type "${type}"`);
  }
  const template = byLocale[locale] ?? byLocale.en;
  return template(params);
}
