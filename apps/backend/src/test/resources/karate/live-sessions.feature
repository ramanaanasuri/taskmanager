Feature: Live Sessions mentor flow (verify -> become mentor -> create -> schedule)

  Background:
    * url baseUrl
    * def auth = { Authorization: '#("Bearer " + token)' }
    * def LocalDateTime = Java.type('java.time.LocalDateTime')

  Scenario: a verified mentor creates and schedules an offering
    # ensure the tester is verified (idempotent) so the participation gate passes
    Given path '/api/reachability/channels'
    And headers auth
    And request { channelsSelected: ['EMAIL'], termsVersion: '1' }
    When method post
    Then status 200

    Given path '/api/reachability/verify/request'
    And headers auth
    And request { channel: 'EMAIL' }
    When method post
    Then status 200
    * def code = response.devCode

    Given path '/api/reachability/verify/confirm'
    And headers auth
    And request { channel: 'EMAIL', code: '#(code)' }
    When method post
    Then status 200
    And match response.verified == true

    # pick a seeded skill
    Given path '/api/skills'
    And headers auth
    When method get
    Then status 200
    And match response.skills == '#[_ > 0]'
    * def skillId = response.skills[0].id

    # become a mentor for that skill
    Given path '/api/mentor/profile'
    And headers auth
    And request { bio: 'Senior architect', skillIds: ['#(skillId)'] }
    When method post
    Then status 200
    And match response.skillIds contains skillId

    # create an offering (future start)
    * def start = LocalDateTime.now().plusDays(2).toString()
    Given path '/api/offerings'
    And headers auth
    And request { skillId: '#(skillId)', title: 'AWS prep live', description: '4h', startTime: '#(start)', durationMin: 240, capacity: 1 }
    When method post
    Then status 201
    And match response.status == 'DRAFT'
    * def offeringId = response.id

    # schedule it -> meeting room created
    Given path '/api/offerings/' + offeringId + '/schedule'
    And headers auth
    When method post
    Then status 200
    And match response.status == 'SCHEDULED'
    And match response.zoomJoinUrl == '#present'

    # it shows up in my offerings
    Given path '/api/my/offerings'
    And headers auth
    When method get
    Then status 200
    And match response.offerings[*].id contains offeringId
