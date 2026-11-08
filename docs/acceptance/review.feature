# Acceptance specification. Automated evidence: tests/integration/broker-flow.mjs
@US02
Feature: Read subscription access for the current account

  Scenario: Authorized or healthy request
    Given a signed-in account has no current plan
    When the client requests GET /api/v1/billing/status
    Then the response status is 200
    And active is false and the account can check its plan status

  Scenario: Boundary or access control
    Given the review environment is available
    When the request omits the access token
    Then the response status is 401
    And plan details are not disclosed
