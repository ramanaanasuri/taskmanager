Feature: Live Sessions enrolment + lobby (browse -> join -> my sessions -> checkin -> lobby)

  Background:
    * url baseUrl
    * def auth = { Authorization: '#("Bearer " + token)' }
    * def LocalDateTime = Java.type('java.time.LocalDateTime')

  Scenario: verified user creates, schedules, browses (link hidden), joins, and enters the lobby
    # verify (idempotent) so the gate passes
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

    # skill + become mentor
    Given path '/api/skills'
    And headers auth
    When method get
    Then status 200
    * def skillId = response.skills[0].id
    Given path '/api/mentor/profile'
    And headers auth
    And request { bio: 'x', skillIds: ['#(skillId)'] }
    When method post
    Then status 200

    # create + schedule
    * def start = LocalDateTime.now().plusDays(3).toString()
    Given path '/api/offerings'
    And headers auth
    And request { skillId: '#(skillId)', title: 'Enrol test', startTime: '#(start)', durationMin: 60, capacity: 3 }
    When method post
    Then status 201
    * def offeringId = response.id
    Given path '/api/offerings/' + offeringId + '/schedule'
    And headers auth
    When method post
    Then status 200
    And match response.status == 'SCHEDULED'

    # browse: our offering shows, and the join URL is hidden
    Given path '/api/offerings'
    And headers auth
    And param skill = skillId
    When method get
    Then status 200
    And match response.offerings[*].id contains offeringId
    * def mine = karate.filter(response.offerings, function(x){ return x.id == offeringId })
    And match mine[0].joinUrl == null

    # join -> enrolled, link now returned
    Given path '/api/offerings/' + offeringId + '/join'
    And headers auth
    When method post
    Then status 201
    And match response.outcome == 'ENROLLED'
    And match response.joinUrl == '#present'

    # my sessions includes it
    Given path '/api/my/sessions'
    And headers auth
    When method get
    Then status 200
    And match response.sessions[*].id contains offeringId

    # check in, then the lobby (as mentor/owner) shows the roster
    Given path '/api/offerings/' + offeringId + '/checkin'
    And headers auth
    When method post
    Then status 200
    And match response.checkedIn == true

    Given path '/api/offerings/' + offeringId + '/lobby'
    And headers auth
    When method get
    Then status 200
    And match response.role == 'MENTOR'
    And match response.mentorCheckedIn == true
