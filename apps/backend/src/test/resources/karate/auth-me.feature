Feature: Identity endpoints

  Background:
    * url baseUrl

  Scenario: /api/auth/me reflects the authenticated principal
    Given path '/api/auth/me'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200

  Scenario: garbage bearer token is rejected
    Given path '/api/auth/me'
    And header Authorization = 'Bearer not.a.jwt'
    When method get
    Then status 401
