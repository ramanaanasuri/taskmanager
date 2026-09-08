Feature: AI metering gate through the public API

  # The tester pseudo-user has no users row by design (rows are created only
  # by OAuth login), so its AI calls are refused by the metering gate with a
  # deterministic 402 BEFORE any model call: no provider quota is consumed,
  # no credit is spent, and the run does not depend on any AI provider.
  # If this scenario ever sees 200, a users row was seeded for the tester -
  # that is environment drift worth failing loudly on.

  Background:
    * url baseUrl

  Scenario: metered endpoints refuse unauthenticated callers
    Given path '/api/ai/parse-task'
    And request { text: 'ping' }
    When method post
    Then status 401

  Scenario: the metering gate refuses a user with no credit, with the upgrade contract
    * configure headers = { Authorization: '#("Bearer " + token)' }
    Given path '/api/ai/parse-task'
    And request { text: 'ping' }
    When method post
    Then status 402
    And match response.code == 'AI_LIMIT_REACHED'
    And match response.aiRequests == { used: '#number', limit: '#number' }

  Scenario: the agent endpoint is behind the same wall
    Given path '/api/ai/chat'
    And request { messages: [ { role: 'user', content: 'hello' } ] }
    When method post
    Then status 401
