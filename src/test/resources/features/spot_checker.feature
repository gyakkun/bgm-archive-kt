Feature: Spot Checker
  As a background job maintaining an archive
  I want to detect holes and randomly check topics
  So that I can verify their existence or catch up missing topics

  Scenario: Generate spot check list with random sampling when no holes in boundary window
    Given the space type is "GROUP"
    And a mock repository with configured max topic id "100"
    And the topic list returned by recent topics API is "10, 20, 30, 95, 96, 97"
    And the bitset of hidden topic mask has "50, 51"
    And the bitset of already spot checked mask has "60, 61"
    When spot check logic is executed
    Then a spot check list file should be created for "GROUP"
    And the spot check list file should contain some random ids between "1" and "100"

  Scenario: Hole check is skipped for EP type
    Given the space type is "EP"
    And the topic list returned by recent topics API is "10, 20, 30, 95, 96, 97"
    When hole check is performed
    Then the hole check result should be empty

  Scenario: Hole check is skipped for CHARACTER type
    Given the space type is "CHARACTER"
    And the topic list returned by recent topics API is "10, 20, 30, 95, 96, 97"
    When hole check is performed
    Then the hole check result should be empty

  Scenario: Hole check is skipped for PERSON type
    Given the space type is "PERSON"
    And the topic list returned by recent topics API is "10, 20, 30, 95, 96, 97"
    When hole check is performed
    Then the hole check result should be empty

  Scenario: Hole check returns empty when topic list is empty
    Given the space type is "BLOG"
    And the topic list returned by recent topics API is empty
    When hole check is performed
    Then the hole check result should be empty

  Scenario: Hole check returns empty when there are no holes
    Given the space type is "BLOG"
    And the topic list returned by recent topics API is "1, 2, 3, 4, 5, 6, 7, 8, 9, 10"
    When hole check is performed
    Then the hole check result should be empty

  Scenario: Hole check detects a small gap as a hole
    Given the space type is "BLOG"
    And the topic list returned by recent topics API is "322415, 322414, 309475, 308773, 322392, 322328, 321363, 322306, 322099, 322404, 322063, 322373, 322407, 320927, 322405, 322406, 322353, 322188, 322403, 322402, 322401, 322400, 322285, 322399, 322398, 314543, 322397, 295446, 321160, 322396, 322123, 318363, 321261, 320903, 320607, 322390, 320794, 322293, 304317, 321896, 322053, 322391, 295093, 322263, 313524, 317692, 322379, 275771, 322387, 322386"
    When hole check is performed
    Then the hole check result should contain "322413"

  Scenario: Hole check clears and resets when checked-ids count exceeds limit
    Given the space type is "SUBJECT"
    And the topic list returned by recent topics API is "2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52"
    When hole check is performed
    And hole check is performed again with the same input
    Then the hole check result should not be empty

  Scenario: Bitset round-trip serialization with single bit
    Given a bitset with bits "1" set
    When the bitset is serialized to a long-list string
    And the string is deserialized back to a bitset
    Then the deserialized bitset should equal the original

  Scenario: Bitset round-trip serialization across word boundary
    Given a bitset with bits "1, 64" set
    When the bitset is serialized to a long-list string
    And the string is deserialized back to a bitset
    Then the deserialized bitset should equal the original

  Scenario: Bitset round-trip serialization with empty bitset
    Given a bitset with no bits set
    When the bitset is serialized to a long-list string
    And the string is deserialized back to a bitset
    Then the deserialized bitset should equal the original

  Scenario: Merge hidden topic masks — visited-and-new wins over not-visited-and-old
    Given an old hidden mask with bits "2, 3" set
    And a new hidden mask with bits "1" set
    And a visited mask with bits "0, 1, 2" set
    When the hidden masks are merged
    Then the merged result bit "0" should be "false"
    And the merged result bit "1" should be "true"
    And the merged result bit "2" should be "false"
    And the merged result bit "3" should be "true"

  Scenario: Spot check sampling size is capped at MAX_SPOT_CHECK_SIZE
    Given the space type is "GROUP"
    And a mock repository with configured max topic id "10000"
    And the topic list returned by recent topics API is "9990, 9991, 9992, 9993, 9994, 9995, 9996, 9997, 9998, 9999, 10000"
    And the bitset of hidden topic mask has no bits set
    And the bitset of already spot checked mask has no bits set
    When spot check logic is executed
    Then the spot check list should contain at most "80" ids

  Scenario: Spot check sampling size is at least MIN_SPOT_CHECK_SIZE
    Given the space type is "GROUP"
    And a mock repository with configured max topic id "10000"
    And the topic list returned by recent topics API is "9990, 9991, 9992, 9993, 9994, 9995, 9996, 9997, 9998, 9999, 10000"
    And the bitset of hidden topic mask has no bits set
    And the bitset of already spot checked mask has no bits set
    When spot check logic is executed
    Then the spot check list should contain at least "10" ids