Feature: Tester authentication (shared by all features via callSingle)

  Scenario: obtain a JWT from the tester auth endpoint
    Given url baseUrl
    And path '/api/tester/auth'
    And request { username: '#(testerUser)', password: '#(testerPassword)' }
    When method post
    Then status 200
    And match response.token == '#string'
    * def token = response.token
