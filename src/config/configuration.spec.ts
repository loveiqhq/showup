import configuration from './configuration';

describe('configuration()', () => {
  it('defaults the OTP length to 6 digits', () => {
    const previous = process.env.OTP_LENGTH;
    delete process.env.OTP_LENGTH;
    try {
      expect(configuration().auth.otpLength).toBe(6);
    } finally {
      if (previous !== undefined) process.env.OTP_LENGTH = previous;
    }
  });

  it('honours an explicit OTP_LENGTH override', () => {
    const previous = process.env.OTP_LENGTH;
    process.env.OTP_LENGTH = '8';
    try {
      expect(configuration().auth.otpLength).toBe(8);
    } finally {
      if (previous === undefined) delete process.env.OTP_LENGTH;
      else process.env.OTP_LENGTH = previous;
    }
  });
});
