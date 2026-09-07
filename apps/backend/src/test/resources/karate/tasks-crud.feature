Feature: Task lifecycle through the public API

  Background:
    * url baseUrl
    * header Authorization = 'Bearer ' + token

  Scenario: create, read, update, complete, delete a task
    # create
    Given path '/api/tasks'
    And request { title: 'Karate: pay the water bill', priority: 'MEDIUM' }
    When method post
    Then status 200
    And match response.id == '#number'
    * def taskId = response.id

    # appears in the list
    Given path '/api/tasks'
    When method get
    Then status 200
    And match response[*].id contains taskId

    # update the title
    Given path '/api/tasks/' + taskId
    And request { title: 'Karate: pay the water bill (updated)' }
    When method put
    Then status 200

    # complete it
    Given path '/api/tasks/' + taskId
    And request { completed: true }
    When method put
    Then status 200

    # delete leaves the list without it
    Given path '/api/tasks/' + taskId
    When method delete
    Then status 200
    Given path '/api/tasks'
    When method get
    Then status 200
    And match response[*].id !contains taskId

  Scenario: unauthenticated request is rejected
    * configure headers = {}
    Given path '/api/tasks'
    When method get
    Then status 401
