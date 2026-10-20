import os
import unittest
from unittest import mock

from app.core.config import DEFAULT_JWT_SECRET, Settings, ai_request_options, validate_settings


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


class AiProviderSettingsTests(unittest.TestCase):
    def config(self, **overrides) -> Settings:
        with mock.patch.dict(os.environ, {}, clear=True):
            return Settings(_env_file=None, **overrides)

    def test_default_is_free_visual_glm_without_paid_fallback(self) -> None:
        config = self.config()
        self.assertEqual(config.ai_api_base_url, "https://open.bigmodel.cn/api/paas/v4/")
        self.assertEqual(config.ai_model, "glm-4.6v-flash")
        self.assertEqual(config.ai_fallback_model, "")
        self.assertEqual(config.ai_api_key, "")
        self.assertEqual(ai_request_options(config), {"thinking": {"type": "disabled"}})

    def test_glm_never_receives_gemini_reasoning_effort(self) -> None:
        config = self.config(ai_reasoning_effort="low")
        self.assertEqual(ai_request_options(config), {"thinking": {"type": "disabled"}})

    def test_gemini_override_keeps_existing_reasoning_option(self) -> None:
        config = self.config(
            ai_api_base_url="https://generativelanguage.googleapis.com/v1beta/openai/",
            ai_model="existing-gemini-model", ai_fallback_model="existing-fallback",
            ai_reasoning_effort="low",
        )
        self.assertEqual(ai_request_options(config), {"reasoning_effort": "low"})
        self.assertEqual(config.ai_model, "existing-gemini-model")
        self.assertEqual(config.ai_fallback_model, "existing-fallback")

    def test_unknown_provider_does_not_receive_thinking_options(self) -> None:
        for base_url in (
            "https://provider.invalid/v1/",
            "https://open.bigmodel.cn.provider.invalid/v1/",
            "https://provider.invalid/generativelanguage.googleapis.com/",
        ):
            with self.subTest(base_url=base_url):
                self.assertEqual(ai_request_options(self.config(ai_api_base_url=base_url)), {})

    def test_glm_thinking_can_be_explicitly_enabled_or_omitted(self) -> None:
        self.assertEqual(
            ai_request_options(self.config(ai_thinking_mode="enabled")),
            {"thinking": {"type": "enabled"}},
        )
        self.assertEqual(ai_request_options(self.config(ai_thinking_mode="")), {})


if __name__ == "__main__":
    unittest.main()
