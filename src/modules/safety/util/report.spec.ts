import {
  isValidReasonForContext,
  latenessAppliesTo,
  REASONS_BY_CONTEXT,
  ReportContext,
  ReportReason,
  ReportStatus,
} from './report';

describe('report enums', () => {
  it('exposes the contexts', () => {
    expect(ReportContext.ActiveDate).toBe('active_date');
    expect(ReportContext.DateReview).toBe('date_review');
  });

  it('exposes the reasons', () => {
    expect(ReportReason.FeltInDanger).toBe('felt_in_danger');
    expect(ReportReason.FeltUncomfortable).toBe('felt_uncomfortable');
    expect(ReportReason.ProfileMismatch).toBe('profile_mismatch');
    expect(ReportReason.ShowedUpLate).toBe('showed_up_late');
  });

  it('exposes the statuses (progress from submitted to resolved)', () => {
    expect(ReportStatus.Submitted).toBe('submitted');
    expect(ReportStatus.Reviewing).toBe('reviewing');
    expect(ReportStatus.Resolved).toBe('resolved');
    expect(ReportStatus.Dismissed).toBe('dismissed');
  });
});

describe('isValidReasonForContext', () => {
  it('accepts the two active-date safety reasons only during an active date', () => {
    expect(
      isValidReasonForContext(
        ReportContext.ActiveDate,
        ReportReason.FeltInDanger,
      ),
    ).toBe(true);
    expect(
      isValidReasonForContext(
        ReportContext.ActiveDate,
        ReportReason.FeltUncomfortable,
      ),
    ).toBe(true);
  });

  it('accepts the two review reasons only in the review flow', () => {
    expect(
      isValidReasonForContext(
        ReportContext.DateReview,
        ReportReason.ProfileMismatch,
      ),
    ).toBe(true);
    expect(
      isValidReasonForContext(
        ReportContext.DateReview,
        ReportReason.ShowedUpLate,
      ),
    ).toBe(true);
  });

  it('rejects a reason used in the wrong context', () => {
    expect(
      isValidReasonForContext(
        ReportContext.ActiveDate,
        ReportReason.ShowedUpLate,
      ),
    ).toBe(false);
    expect(
      isValidReasonForContext(
        ReportContext.DateReview,
        ReportReason.FeltInDanger,
      ),
    ).toBe(false);
  });

  it('every context lists at least one reason', () => {
    for (const context of Object.values(ReportContext)) {
      expect(REASONS_BY_CONTEXT[context].length).toBeGreaterThan(0);
    }
  });
});

describe('latenessAppliesTo', () => {
  it('is required only for the showed-up-late reason', () => {
    expect(latenessAppliesTo(ReportReason.ShowedUpLate)).toBe(true);
    expect(latenessAppliesTo(ReportReason.ProfileMismatch)).toBe(false);
    expect(latenessAppliesTo(ReportReason.FeltInDanger)).toBe(false);
  });
});
