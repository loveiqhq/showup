import { EmailService } from './email.service';

describe('EmailService (transactional convenience API)', () => {
  let dispatch: { sendEmail: jest.Mock };
  const make = () => new EmailService(dispatch as any);

  beforeEach(() => {
    dispatch = {
      sendEmail: jest.fn().mockResolvedValue({ status: 'sent', delivered: 1 }),
    };
  });

  it('sends an email-verification message with the code', async () => {
    await make().sendEmailVerification('u1', '123456');
    expect(dispatch.sendEmail).toHaveBeenCalledWith(
      'u1',
      'email_verification',
      {
        code: '123456',
      },
    );
  });

  it('sends a password-reset message with the code', async () => {
    await make().sendPasswordReset('u1', '999');
    expect(dispatch.sendEmail).toHaveBeenCalledWith('u1', 'password_reset', {
      code: '999',
    });
  });

  it('sends an account-closure message', async () => {
    await make().sendAccountClosure('u1');
    expect(dispatch.sendEmail).toHaveBeenCalledWith(
      'u1',
      'account_closure',
      {},
    );
  });

  it('sends a safety alert with an optional detail', async () => {
    await make().sendSafetyAlert('u1', 'Please review your recent date.');
    expect(dispatch.sendEmail).toHaveBeenCalledWith('u1', 'safety_alert', {
      detail: 'Please review your recent date.',
    });
  });
});
