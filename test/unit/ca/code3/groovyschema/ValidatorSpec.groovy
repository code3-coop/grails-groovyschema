package ca.code3.groovyschema

import groovy.util.*
import spock.lang.*
import org.codehaus.groovy.grails.web.json.*

class ValidatorSpec extends Specification {
  def validator

  def setup() {
    validator = new Validator()
  }

  def "it returns the correct error message"() {
    setup:
    def errorsSchema = [
      type: 'array',
      minItems: 0,
      required: true,
      items: [
        type: 'object',
        required: true,
        additionalProperties: false,
        properties: [
          instance: [type:'any'], // the validated (sub-)instance e.g. "abc"
          schema: [type:'object', required:true], // the associated (sub-)schema e.g. [format:'email']
          message: [type:'string', required:true] // an error message e.g. "does not match 'email' pattern"
        ]
      ]
    ]

    expect:
    def errors = validator.validate(instance, schema)
    errors.size() == 1
    errors[0].message == message
    errors[0].schema == schema
    errors[0].instance == instance

    def metaErrors = validator.validate(errors, errorsSchema)
    metaErrors.size() == 0

    where:
    schema                            | instance | message
    [type:'string']                   | 0        | "is not of type 'string'"
    [divisibleBy:2]                   | 3        | "is not divisible by 2"
    [maximum:0]                       | 1        | "is greater than 0"
    [minimum:1]                       | 0        | "is less than 1"
    [maxLength:0]                     | "a"      | "exceeds maximum length of 0"
    [minLength:1]                     | ""       | "does not meet minimum length of 1"
    [maxItems:0]                      | [1]      | "exceeds maximum length of 0"
    [minItems:1]                      | []       | "does not meet minimum length of 1"
    [format:'email']                  | ""       | "does not match 'email' format"
    [pattern:/a+/]                    | "b"      | "does not match pattern /a+/"
    [required:true]                   | null     | "is required"
    [additionalProperties:false]      | [a:1]    | "additional properties ([a]) are not allowed"
    [additionalItems:false, items:[]] | [1]      | "additional items are not allowed"
    [uniqueItems:true]                | [1, 1]   | "contains duplicate items"
    [fixed:"a"]                       | "b"      | "is not 'a'"
    [enum:['a', 'b']]                 | ''       | "is not one of [a, b]"
    [dependencies:[a:'b']]            | [a:1]    | "'a' depends on the presence of 'b'"
    [not:[fixed:'a']]                 | 'a'      | "complies to one or more prohibited schemas"
    [oneOf:[[fixed:'a']]]             | 'b'      | "does not comply to exactly one of the given schemas"
    [anyOf:[[fixed:'a']]]             | 'b'      | "does not comply to any of the given schemas"
    [allOf:[[fixed:'a']]]             | 'b'      | "does not comply to all of the given schemas"
  }

  def "it validates the `type` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance   | schema           | errCount
    'foo'      | [type:'string']  | 0
    1.0        | [type:'number']  | 0
    1.0        | [type:'integer'] | 1
    1          | [type:'integer'] | 0
    true       | [type:'boolean'] | 0
    [1,2,3]    | [type:'array']   | 0
    null       | [type:'null']    | 0
    1          | [type:'any']     | 0
    null       | [type:'number']  | 0
    [x:1, y:2] | [type:'object']  | 0
  }

  def "it disallows unknown `type` attribute values"() {
    when:
    validator.validate(1, [type:'foo'])

    then:
    thrown(IllegalArgumentException)
  }

  def "it validates the `minimum` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema      | errCount
    1        | [minimum:0] | 0
    1        | [minimum:1] | 0
    1        | [minimum:2] | 1
  }

  def "it validates the `exclusiveMinimum` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema                             | errCount
    1        | [minimum:0, exclusiveMinimum:true] | 0
    1        | [minimum:1, exclusiveMinimum:true] | 1
    1        | [minimum:2, exclusiveMinimum:true] | 1
  }

  def "it validates the `maximum` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema      | errCount
    1        | [maximum:0] | 1
    1        | [maximum:1] | 0
    1        | [maximum:2] | 0
  }

  def "it validates the `exclusiveMaximum` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema                             | errCount
    1        | [maximum:0, exclusiveMaximum:true] | 1
    1        | [maximum:1, exclusiveMaximum:true] | 1
    1        | [maximum:2, exclusiveMaximum:true] | 0
  }

  def "it validates the `divisibleBy` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema          | errCount
    0        | [divisibleBy:2] | 0
    1        | [divisibleBy:2] | 1
  }

  def "it validates the `required` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema           | errCount
    null     | [required:true]  | 1
    1        | [required:true]  | 0
    null     | [required:false] | 0
    1        | [required:false] | 0
  }

  def "it validates the `pattern` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema            | errCount
    "foo"    | [pattern:/^fo+$/] | 0
    "bar"    | [pattern:/^fo+$/] | 1
  }

  def "it validates the `format` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance      | schema               | errCount
    "foo@bar.com" | [format:'email']     | 0
    "foo@bar.com" | [format:'date-time'] | 1
  }

  def "it validates the `minLength` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema        | errCount
    "a"      | [minLength:0] | 0
    "a"      | [minLength:1] | 0
    "a"      | [minLength:2] | 1
  }

  def "it validates the `exclusiveMinimum` attribute with minLength"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema                               | errCount
    "a"      | [minLength:0, exclusiveMinimum:true] | 0
    "a"      | [minLength:1, exclusiveMinimum:true] | 1
    "a"      | [minLength:2, exclusiveMinimum:true] | 1
  }

  def "it validates the `maxLength` attribute"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema        | errCount
    "a"      | [maxLength:0] | 1
    "a"      | [maxLength:1] | 0
    "a"      | [maxLength:2] | 0
  }

  def "it validates the `exclusiveMaximum` attribute with maxLength"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | schema                               | errCount
    "a"      | [maxLength:0, exclusiveMaximum:true] | 1
    "a"      | [maxLength:1, exclusiveMaximum:true] | 1
    "a"      | [maxLength:2, exclusiveMaximum:true] | 0
  }

  def "it validates the `properties` attribute"() {
    setup:
    def schema = [
      type: 'object',
      properties: [
        foo: [type:'string', pattern:/^fo+$/, required:true],
        bar: [type:'number', minimum:10]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance            | errCount
    [foo:'foo', bar:10] | 0
    [foo:'bar', bar:10] | 1
    [foo:'bar', bar:0]  | 2
  }

  def "it validates the `additionalProperties` attribute"() {
    setup:
    def schema = [
      type: 'object',
      additionalProperties: additionnal,
      properties: [
        a: [type:'number'],
        b: [type:'number']
      ]
    ]
    
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    additionnal     | instance        | errCount
    true            | [a:1, b:2, c:3] | 0
    [type:'string'] | [:]             | 0
    false           | [:]             | 0
    []              | [:]             | 0
    false           | [a:1]           | 0
    false           | [a:1, b:2]      | 0
    false           | [a:1, b:2, c:3] | 1
    ['c']           | [a:1, b:2, c:3] | 0
    ['x']           | [a:1, b:2, c:3] | 1
    ['c', 'd']      | [a:1, b:2, c:3] | 0
    [type:'number'] | [a:1, c:'3']    | 1
    [type:'string'] | [a:1, c:'3']    | 0
  }

  def "it validates the `items` attribute"() {
    setup:
    def schema = [
      type: 'array',
      additionalItems: additional,
      items: itemSchema
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    itemSchema                         | additional | instance  | errCount
    [type:'number']                    | null       | [1, 2, 3] | 0
    [type:'string']                    | null       | [1, 2, 3] | 3
    [[type:'number'], [type:'string']] | false      | [1, '2']  | 0
    [[type:'number'], [type:'string']] | false      | [1, 2]    | 1
    [[type:'number']]                  | false      | [1, 2]    | 1
    [[type:'number']]                  | null       | [1, 2]    | 1
    [[type:'number']]                  | true       | [1, 2]    | 0
  }

  def "it validates the `minItems` attribute"() {
    setup:
    def schema = [
      type: 'array',
      minItems: minItems,
      exclusiveMinimum: exclusiveMin
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    minItems | exclusiveMin | instance | errCount
    0        | false        | []       | 0
    1        | false        | []       | 1
    1        | false        | [1]      | 0
    1        | true         | [1]      | 1
  }

  def "it validates the `maxItems` attribute"() {
    setup:
    def schema = [
      type: 'array',
      maxItems: maxItems,
      exclusiveMaximum: exclusiveMax
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    maxItems | exclusiveMax | instance | errCount
    1        | false        | [1]      | 0
    1        | false        | [1, 2]   | 1
    2        | false        | [1, 2]   | 0
    2        | true         | [1, 2]   | 1
  }

  def "it deeply compares things"() {
    expect:
    validator.deepEqual(a, b) == areEqual

    where:
    a              | b              | areEqual
    's'            | 's'            | true
    1              | 1              | true
    1              | 2              | false
    [1, 2, 3]      | [1, 2, 3]      | true
    [1, 2, 3]      | 's'            | false
    [1, 2, 3]      | 1              | false
    [1, 2, 3]      | [1]            | false
    [[1,2], [3,4]] | [[1,2], [3,4]] | true
    [[1,2], [3,4]] | [[1,2], [5,6]] | false
    [a:1, b:2]     | [a:1, b:2]     | true
    [a:1, b:2]     | [a:1]          | false
    [a:1]          | [a:1, b:2]     | false
    [a:1, b:2]     | 's'            | false
    [a:[1,2,3]]    | [a:[1,2,3]]    | true
    [a:[1,2,3]]    | [a:[1,2,'s']]  | false
  }

  def "it validates the `uniqueItems` attribute"() {
    setup:
    def schema = [
      type: 'array',
      uniqueItems: uniqueItems
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    uniqueItems | instance       | errCount
    true        | [1,2,3]        | 0
    true        | [1,1,1]        | 1
    false       | [1,1,1]        | 0
    true        | [[1,2], [1,2]] | 1
    true        | [[1,2], 1, 2]  | 0
  }

  def "it validates the `enum` attribute"() {
    setup:
    def schema = [
      enum: enumeration
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    enumeration       | instance | errCount
    [1,2,3]           | 1        | 0
    [1,2,3]           | 4        | 1
    [[1,2], [3,4], 5] | [1,2]    | 0
    [[1,2], [3,4], 5] | 1        | 1
  }

  def "it skips enum validation when instance is null"() {
    setup:
    def schema = [
      enum: enumeration,
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    enumeration | instance | errCount
    [1,2,3]     | null     | 0
  }

  def "it validates the `fixed` attribute"() {
    setup:
    def schema = [
      fixed: fixed
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    fixed | instance | errCount
    1     | 1        | 0
    0     | 1        | 1
    [1,2] | [1,2]    | 0
    [0,1] | [1,2]    | 1
  }

  def "it validates the `patternProperties` attribute"() {
    setup:
    def schema = [
      additionalProperties: additional,
      patternProperties: [
        /^fo+$/:  [type:'string'],
        /^bar$/:  [type:'string'],
        /^fooo$/: [type:'string'],
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    additional      | instance               | errCount
    null            | [foo:'foo']            | 0
    null            | [foo:1]                | 1
    null            | [foo:'foo', bar:'bar'] | 0
    null            | [foo:'foo', bar:1]     | 1
    null            | [foo:1, bar:1]         | 2
    false           | [foo:'foo', other:1]   | 1
    ['other']       | [foo:'foo', other:1]   | 0
    [type:'number'] | [foo:'foo', other:1]   | 0
    [type:'string'] | [foo:'foo', other:1]   | 1
    false           | [fooo:'s']             | 0
    false           | [fooo:1]               | 2
  }

  def "it validates the `dependencies` attribute for property dependencies"() {
    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    schema                       | instance        | errCount
    [dependencies:[a:'b']]       | [a:1, b:2]      | 0
    [dependencies:[a:'b']]       | [a:1]           | 1
    [dependencies:[a:['b','c']]] | [a:1, b:2, c:3] | 0
    [dependencies:[a:['b','c']]] | [a:1, b:2]      | 1
    [dependencies:[a:['b','c']]] | [a:1]           | 1
    [dependencies:[a:['b','c']]] | [b:2]           | 0
  }

  def "it validates the `dependencies` attribute for schema dependencies"() {
    // If 'a' is present the instance must have a `b property of type `number`
    setup:
    def schema = [
      dependencies: [
        a: [
          properties: [
            b: [type:'number', required:true]
          ]
        ]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance     | errCount
    [a:1, b:2]   | 0
    [b:2]        | 0
    [a:1]        | 1
    [a:1, b:'s'] | 1
  }

  def "it validates the `allOf` attribute"() {
    setup:
    def schema = [
      allOf: [
        [type:'number', maximum:5],
        [type:'number', maximum:10]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | errCount
    5        | 0
    6        | 1
    11       | 1
  }

  def "it validates the `anyOf` attribute"() {
    setup:
    def schema = [
      anyOf: [
        [type:'number', maximum:5],
        [type:'number', maximum:10]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | errCount
    5        | 0
    6        | 0
    11       | 1
  }

  def "it validates the `oneOf` attribute"() {
    setup:
    def schema = [
      oneOf: [
        [type:'number', maximum:5],
        [type:'number', maximum:10]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | errCount
    5        | 1
    6        | 0
    11       | 1
  }

  def "it validates the `not` attribute"() {
    setup:
    def schema = [
      not: [
        [type:'number', maximum:5],
        [type:'number', maximum:10]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance | errCount
    5        | 1
    6        | 1
    11       | 0
  }

  def "it works with instances of type JSONObject"() {
    // If 'a' is present the instance must have a `b property of type `number`
    setup:
    def schema = [
      dependencies: [
        a: [
          properties: [
            b: [type:'number', required:true]
          ]
        ]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance                     | errCount
    new JSONObject([a:1, b:2])   | 0
    new JSONObject([b:2])        | 0
    new JSONObject([a:1])        | 1
    new JSONObject([a:1, b:'s']) | 1
  }

  def "it validates nested and mixed instances"() {
    setup:
    def schema = [
      type: 'object',
      required: true,
      dependencies: [
        c: [
          properties: [
            d: [type:'number', minimum:3, required:true]
          ]
        ]
      ],
      additionalProperties: ['c', 'd'],
      properties: [
        a: [
          required: true,
          type: 'array',
          minItems: 1,
          maxItems: 2,
          items: [
            type: 'object',
            properties: [
              b: [
                oneOf: [
                  [type:'number', maximum:3, required:true],
                  [type:'string', pattern:/^fo+$/, required:true]
                ]
              ]
            ]
          ]
        ]
      ]
    ]

    expect:
    validator.validate(instance, schema).size() == errCount

    where:
    instance                      | errCount
    [a:[[b:1], [b:2]]]            | 0
    [a:[[b:1], [b:2], [b:3]]]     | 1
    [a:[[b:1], [b:100]]]          | 1
    [a:[[b:1], [b:2], [b:100]]]   | 2
    [a:[[b:1], [b:100], [b:100]]] | 3
    null                          | 1
    [a:null]                      | 1
    [a:1]                         | 1
    [a:[1]]                       | 1
    [a:[[c:3]]]                   | 1
    [a:[[b:'foo']]]               | 0
    [a:[[b:'bar']]]               | 1
    [a:[[b:1]], c:1, d:3]         | 0
    [a:[[b:1]], c:1, d:0]         | 1
    [a:[[b:1]], d:0]              | 0
  }

}
