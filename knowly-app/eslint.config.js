// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const security = require('eslint-plugin-security');

// Sinks that bypass Angular's built-in DomSanitizer / template escaping.
// Angular sanitizes bindings by default, so these are the only real XSS
// vectors in this stack. Any legitimate use MUST be reviewed by appsec and
// suppressed with a scoped `// eslint-disable-next-line` referencing that
// review, not a blanket disable.
const XSS_SINK_MESSAGE =
  'This bypasses Angular sanitization and is a real XSS vector. It requires an explicit exception reviewed by appsec — do not disable this rule without one.';

const restrictedXssSyntax = [
  {
    selector:
      "CallExpression[callee.property.name=/^bypassSecurityTrust(Html|Url|ResourceUrl|Script|Style)$/]",
    message: XSS_SINK_MESSAGE,
  },
  {
    // Covers both `el.innerHTML = ...` and `this.el.nativeElement.innerHTML = ...`
    // (property.name matches regardless of how deep the member expression is).
    selector: "AssignmentExpression[left.property.name='innerHTML']",
    message: XSS_SINK_MESSAGE,
  },
];

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
      security.configs.recommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
      'no-restricted-syntax': ['error', ...restrictedXssSyntax],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
