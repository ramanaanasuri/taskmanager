Feature: Public mentor profile (learner views a mentor's credentials before joining)

  Background:
    * url baseUrl
    * def auth = { Authorization: '#("Bearer " + token)' }
    * def LocalDateTime = Java.type('java.time.LocalDateTime')

  Scenario: a learner can read a mentor's public profile (bio + skills + open sessions)
    # verify the tester (idempotent) so mentor actions pass the gate
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

    # pick a seeded skill
    Given path '/api/skills'
    And headers auth
    When method get
    Then status 200
    * def skillId = response.skills[0].id
    * def skillName = response.skills[0].name

    # become a mentor with a bio + that skill; capture our own email from the view
    Given path '/api/mentor/profile'
    And headers auth
    And request { bio: 'Long-time investor, 20+ years', skillIds: ['#(skillId)'] }
    When method post
    Then status 200
    * def mentorEmail = response.mentorEmail

    # create + schedule an offering so the profile has an open session
    * def Instant = Java.type('java.time.Instant')
    * def DateTimeFormatter = Java.type('java.time.format.DateTimeFormatter')
    * def ZoneOffset = Java.type('java.time.ZoneOffset')
    * def utcFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    * def start = utcFmt.format(Instant.now().plusSeconds(172800))
    Given path '/api/offerings'
    And headers auth
    And request { skillId: '#(skillId)', title: 'Options income live', description: 'weeklies', startTime: '#(start)', durationMin: 60, capacity: 5 }
    When method post
    Then status 201
    * def offeringId = response.id

    Given path '/api/offerings/' + offeringId + '/schedule'
    And headers auth
    When method post
    Then status 200

    # NEW ENDPOINT: read the public mentor profile
    Given path '/api/mentors/profile'
    And headers auth
    And param email = mentorEmail
    When method get
    Then status 200
    And match response.mentorEmail == mentorEmail
    And match response.bio == 'Long-time investor, 20+ years'
    And match response.skills contains skillName
    And match response.sessions[*].id contains offeringId
    And match response.sessions[0].seatsLeft == '#number'

  Scenario: unknown mentor returns 404
    Given path '/api/mentors/profile'
    And headers auth
    And param email = 'nobody-here@example.com'
    When method get
    Then status 404
