import unittest

from app.core.config import DEFAULT_JWT_SECRET, Settings, validate_settings


class JwtSecretValidationTests(unittest.TestCase):
    def _validate(self, **overrides) -> Settings:
        # _env_file=None keeps a developer's local server/.env out of the test.
        return validate_settings(Settings(_env_file=None, **overrides))

    def test_production_rejects_placeholder_secret(self) -> None:
        with self.assertRaises(RuntimeError):
            self._validate(environment="production", jwt_secret=DEFAULT_JWT_SECRET)

    def test_production_rejects_blank_secret(self) -> None:
        with self.assertRaises(RuntimeError):
            self._validate(environment=" Production ", jwt_secret="   ")

    def test_error_message_does_not_echo_other_settings(self) -> None:
        with self.assertRaises(RuntimeError) as caught:
            self._validate(
                environment="production",
                jwt_secret=DEFAULT_JWT_SECRET,
                ai_api_key="sk-should-not-leak",
            )
        self.assertNotIn("sk-should-not-leak", str(caught.exception))

    def test_production_accepts_real_secret(self) -> None:
        settings = self._validate(environment="production", jwt_secret="x" * 48)
        self.assertEqual(settings.jwt_secret, "x" * 48)

    def test_development_keeps_placeholder_default(self) -> None:
        settings = self._validate(environment="development")
        self.assertEqual(settings.jwt_secret, DEFAULT_JWT_SECRET)


if __name__ == "__main__":
    unittest.main()
