Feature: Reachability verification (email OTP loop via the tester echo)

  Background:
    * url baseUrl
    * def auth = { Authorization: '#("Bearer " + token)' }

  Scenario: select, request, confirm, and reach the verified floor
    # opt in to the EMAIL channel
    Given path '/api/reachability/channels'
    And headers auth
    And request { channelsSelected: ['EMAIL'], termsVersion: '1' }
    When method post
    Then status 200

    # request a code — the tester subject gets it echoed back as devCode
    Given path '/api/reachability/verify/request'
    And headers auth
    And request { channel: 'EMAIL' }
    When method post
    Then status 200
    And match response.devCode == '#string'
    * def code = response.devCode

    # confirm with the correct code
    Given path '/api/reachability/verify/confirm'
    And headers auth
    And request { channel: 'EMAIL', code: '#(code)' }
    When method post
    Then status 200
    And match response.verified == true

    # floor is now met
    Given path '/api/reachability/self'
    And headers auth
    When method get
    Then status 200
    And match response.floorMet == true

  Scenario: a wrong code does not verify
    Given path '/api/reachability/verify/request'
    And headers auth
    And request { channel: 'EMAIL' }
    When method post
    Then status 200

    Given path '/api/reachability/verify/confirm'
    And headers auth
    And request { channel: 'EMAIL', code: '000000' }
    When method post
    Then status 200
    And match response.verified == false
