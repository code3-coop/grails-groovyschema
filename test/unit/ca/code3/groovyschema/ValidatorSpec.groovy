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
    expect:
    def errors = validator.validate(instance, schema)
    errors.size() == 1
    errors[0].message == message
    errors[0].schema == schema
    errors[0].instance == instance
    errors[0].path == path

    where:
    schema                            | instance | path   | message
    [type:'string']                   | 0        | "this" | "groovyschema.type.message"
    [divisibleBy:2]                   | 3        | "this" | "groovyschema.divisibleBy.message"
    [maximum:0]                       | 1        | "this" | "groovyschema.maximum.message"
    [minimum:1]                       | 0        | "this" | "groovyschema.minimum.message"
    [maxLength:0]                     | "a"      | "this" | "groovyschema.maxLength.message"
    [minLength:1]                     | ""       | "this" | "groovyschema.minLength.message"
    [maxItems:0]                      | [1]      | "this" | "groovyschema.maxItems.message"
    [minItems:1]                      | []       | "this" | "groovyschema.minItems.message"
    [format:'email']                  | ""       | "this" | "groovyschema.format.message"
    [pattern:/a+/]                    | "b"      | "this" | "groovyschema.pattern.message"
    [required:true]                   | null     | "this" | "groovyschema.required.message"
    [additionalProperties:false]      | [a:1]    | "this" | "groovyschema.additionalProperties.message"
    [additionalItems:false, items:[]] | [1]      | "this" | "groovyschema.additionalItems.message"
    [uniqueItems:true]                | [1, 1]   | "this" | "groovyschema.uniqueItems.message"
    [fixed:"a"]                       | "b"      | "this" | "groovyschema.fixed.message"
    [enum:['a', 'b']]                 | ''       | "this" | "groovyschema.enum.message"
    [dependencies:[a:'b']]            | [a:1]    | "this" | "groovyschema.dependencies.message"
    [not:[[fixed:'a']]]               | 'a'      | "this" | "groovyschema.not.message"
    [oneOf:[[fixed:'a']]]             | 'b'      | "this" | "groovyschema.oneOf.message"
    [anyOf:[[fixed:'a']]]             | 'b'      | "this" | "groovyschema.anyOf.message"
    [allOf:[[fixed:'a']]]             | 'b'      | "this" | "groovyschema.allOf.message"
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

  def "it throws an exception when divisibleBy:0"() {
    setup:
    def schema = [divisible:0]

    when:
    validator.validate(1, schema)

    then:
    def exception = thrown(IllegalArgumentException)
    exception.message == "schema instance does not comply to meta-schema"
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

  def "it calculates the path for properties"() {
    setup:
    def schema = [
      type: 'object',
      properties: [
        foo: [type:'string', pattern:/^fo+$/, required:true],
        bar: [type:'number', minimum:10],
        qux: [
          type: 'object',
          properties: [
            bar: [type:'number']
          ]
        ]
      ]
    ]
    
    expect:
    validator.validate(instance, schema)[0].path == path

    where:
    instance                           | path
    [foo:1]                            | 'this.foo'
    [bar:10]                           | 'this.foo'
    [foo:'foo', bar:'s']               | 'this.bar'
    [foo:'foo', bar:10, qux:[bar:'s']] | 'this.qux.bar'
  }

  def "it calculates the path for items"() {
    setup:
    def schema = [
      type: 'array',
      items: itemSchema
    ]

    expect:
    validator.validate(instance, schema)[0].path == path

    where:
    itemSchema                                      | instance         | path
    [type:'number']                                 | ['s', 2, 3]      | 'this.0'
    [type:'number']                                 | [1, 2, 's']      | 'this.2'
    [type:'object', properties:[a:[type:'number']]] | [[a:1], [a:'s']] | 'this.1.a'
    [[type:'number'], [type:'string']]              | [1, 2]           | 'this.1'
  }

  def "it allows the object schema do specify required attributes"() {
    setup:
    def schema = [
      type: 'object',
      required: required,
    ]

    expect:
    def errors = validator.validate(instance, schema).size() == errCount

    where:
    required       | instance   | errCount
    false          | null       | 0
    true           | [:]        | 0
    ['foo']        | [foo:1]    | 0
    ['foo']        | [foo:null] | 1
    ['foo']        | [:]        | 1
    ['foo', 'bar'] | null       | 1
    ['foo', 'bar'] | [:]        | 2
    ['foo', 'bar'] | [qux:1]    | 2
    ['foo', 'bar'] | [foo:1]    | 1
    ['foo', 'bar'] | [bar:1]    | 1
  }

  def "it dtermines the path for required properties"() {
    setup:
    def schema = [
      type: 'object',
      required: required,
    ]

    expect:
    validator.validate(instance, schema)[0].path == path

    where:
    required       | instance | path
    ['foo', 'bar'] | [foo:1]  | 'this.bar'
    ['foo', 'bar'] | [bar:1]  | 'this.foo'
    ['foo', 'bar'] | [:]      | 'this.foo'
    ['foo', 'bar'] | null     | 'this'
  }

  def "it meta-meta validates"() {
    when:
    def metaMetaErrors = validator.validate(Validator.META_SCHEMA, Validator.META_SCHEMA)
    def metaErrors = validator.validate(Validator.ERRORS_SCHEMA, Validator.META_SCHEMA)

    then:
    metaMetaErrors.size() == 0
    metaErrors.size() == 0
  }
}
