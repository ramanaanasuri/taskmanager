Feature: InsightHub per-topic authorization — happy path through the authz seam

  Background:
    * url baseUrl
    * def auth = ({ Authorization: 'Bearer ' + token })

  Scenario: create hub, ask with a topic, and a granted reviewer may act
    # creating a hub makes the tester a mentor AND grants hubadmin on active topics
    Given path '/api/insight-hubs'
    And headers auth
    And request { name: 'Authz Test Hub' }
    When method post
    Then status 201
    * def hubId = response.id

    # ask — topic (skillId) defaults to the active default topic and is persisted
    Given path '/api/insight-hubs/' + hubId + '/questions'
    And headers auth
    And request { text: 'What is a covered call and how does assignment work?' }
    When method post
    Then status 201
    * def qid = response.id
    And match response.skillId == '#present'

    # the granted reviewer (the creator) passes the per-topic gate -> NOT 403
    Given path '/api/questions/' + qid + '/answer'
    And headers auth
    And request { text: 'A covered call sells upside on shares you own.' }
    When method post
    Then status 200
    And match response.status == 'DELIVERED'

  Scenario: any authed user reads topics and asks into the default hub (auto-enroll)
    Given path '/api/insight-hubs/topics'
    And headers auth
    When method get
    Then status 200
    And match response.topics == '#present'
    And match response.defaultHubId == '#present'
    And match response.defaultHubName == '#present'
    * def defHub = response.defaultHubId
    * def topicId = response.topics[0].id + ''

    Given path '/api/insight-hubs/' + defHub + '/questions'
    And headers auth
    And request { text: 'Auto-enroll ask — any authed user', skillId: '#(topicId)' }
    When method post
    Then status 201
    And match response.skillId == '#present'
