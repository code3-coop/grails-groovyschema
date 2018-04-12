package ca.code3.groovyschema

class Validator {

  def isMetaValidating = true

  // Takes in a `schema` and a potential `instance` of it. Validates the top
  // level constraints.

  def validate(instance, schema, path = "this") {
    if (isMetaValidating) { metaValidate(schema, META_SCHEMA, "schema") }

    def errors = schema.findAll(relevantProperties).collect { key, val ->
      def validator = this["validate${key.capitalize()}"]
      if (validator) {
        def err = validator(instance, schema, path)
        (err instanceof String || err instanceof GString) ? mkError(err, instance, schema, path) : err
      } else {
        throw new IllegalArgumentException("Unknown validation attribute '${key}'")
      }
    }.findAll().flatten()

    if (isMetaValidating) { metaValidate(errors, ERRORS_SCHEMA, "errors") }
    errors
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `allOf` schemas. The instance is valid only if it
  // conforms to all. Returns the validation error messages, if any.

  private validateAllOf = { instance, schema, path ->
    def errors = collectValidations(instance, schema.allOf, path).findAll()

    if (errors.size() > 0) {
      "groovyschema.allOf.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `anyOf` schemas. The instance is valid if it
  // conforms to at least one. Returns the validation error messages, if any.

  private validateAnyOf = { instance, schema, path ->
    def errors = collectValidations(instance, schema.anyOf, path).findAll()

    if (errors.size() == schema.anyOf.size()) {
      "groovyschema.anyOf.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `oneOf` schemas. The instance is valid if it
  // conforms to exactly one. Returns the validation error messages, if any.

  private validateOneOf = { instance, schema, path ->
    def errors = collectValidations(instance, schema.oneOf, path).findAll()

    if (schema.oneOf.size() - errors.size() != 1) {
      "groovyschema.oneOf.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `not` schemas. The instance is valid only if it
  // *does not* conform to all. Returns the validation error message, if any.

  private validateNot = { instance, schema, path ->
    def prohibitedSchemas = schema.not instanceof List ? schema.not : [schema.not]
    def errors = collectValidations(instance, prohibitedSchemas, path).findAll()

    if (errors.size() != prohibitedSchemas.size()) {
      "groovyschema.not.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates
  // dependencies to certain properties. Dependencies are either a list of
  // property names that must be present in the instance or additional schemas
  // to which the passed-in instance must comply to. Returns the validation
  // error message, if any.

  private validateDependencies = { instance, schema, path ->
    if (!(instance instanceof Map)) return

    schema.dependencies.collect { property, description ->
      def dependency = instance[property]
      if (dependency != null) {
        if (description instanceof String || description instanceof List) {
          if (! description.every { instance[it] != null }) {
            mkError("groovyschema.dependencies.message", instance, schema, path)
          }
        } else {
          this.validate(instance, description, path)
        }
      }
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // instance is one of the enumerated values. Returns the validation error
  // message, if any.

  private validateEnum = { instance, schema, path ->
    if (! schema.enum.any { deepEqual(it, instance) } && instance != null) {
      "groovyschema.enum.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // instance is equal to the `fixed` value. Returns the validation error
  // message, if any.

  private validateFixed = { instance, schema, path ->
    if (! deepEqual(schema.fixed, instance)) {
      "groovyschema.fixed.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that items
  // in the instance are unique. Returns the validation error message, if any.

  private validateUniqueItems = { instance, schema, path ->
    if (!(instance instanceof List)) return
    if (! schema.uniqueItems) return

    def uniqued = instance.unique(false, { a, b -> deepEqual(a, b) ? 0 : 1 })

    if (instance.size() != uniqued.size()) {
      "groovyschema.uniqueItems.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates items in a
  // List. The `items` property can be a schema that describes all items in the
  // instance or an ordered list of schemas, one for each item in the instance.
  // If `items` is a list of schemas, additional items in the instance are
  // allowed only if the `additionalItems` property is `true`. Returns the
  // validation error message, if any.

  private validateItems = { instance, schema, path ->
    if (!(instance instanceof List)) return

    def items = instance
    if (schema.items instanceof Map) {
      def itemSchema = schema.items
      items.collect { item -> this.validate(item, itemSchema, path) }.findAll().flatten()
    } else if (items.size() > schema.items.size() && !schema.additionalItems) {
      "groovyschema.additionalItems.message"
    } else {
      def schemas = schema.items ?: []
      [schemas, items].transpose().collect {
        def itemSchema = it[0]
        def item = it[1]
        this.validate(item, itemSchema, path)
      }
    }
  }

  // Takes in a `schema` and a potential `instance` of it. `patternProperties`
  // holds a hash mapping regular expressions to schemas. All of the matching
  // instance properties will be validated. Returns the validation error
  // message, if any.

  private validatePatternProperties = { instance, schema, path ->
    if (!(instance instanceof Map)) return

    instance.collect { property, propertyValue ->
      schema.patternProperties.collect { pattern, propertySchema ->
        if (property ==~ pattern) {
          this.validate(propertyValue, propertySchema, path)
        }
      }.findAll().flatten()
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that
  // the given instance does not have any additional properties. Tested
  // properties are filtered to remove the ones matching patterns in the
  // `patternProperties` attribute. `additionalProperties` can be a boolean,
  // a list of valid additional properties or a schema describing additional
  // properties. Returns the validation error message, if any.

  private validateAdditionalProperties = { instance, schema, path ->
    if (!(instance instanceof Map)) return
    if (schema.additionalProperties == true) return

    def givenPatterns = schema.patternProperties?.keySet() ?: []
    def given = instance.keySet().findAll { property -> !givenPatterns.any { pattern -> property ==~ pattern } }

    if (schema.additionalProperties == false || (schema.additionalProperties instanceof List)) {
      def possible = (schema.properties?.keySet() ?: []) + (schema.additionalProperties ?: [])
      def additional = given - possible

      if (additional.size() != 0) {
       "groovyschema.additionalProperties.message"
      }
    } else {
      def additionalPropertySchema = schema.additionalProperties
      def possible = schema.properties?.keySet() ?: []
      def additional = given - possible

      additional.collect { property ->
        this.validate(instance[property], additionalPropertySchema, path)
      }.findAll().flatten()
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Loops through
  // given properties and calls `validate` for each passing along its (sub-)
  // schema. Returns the validation error message, if any.

  private validateProperties = { instance, schema, path ->
    if (!(instance instanceof Map)) return

    schema.properties.collect { property, propertySchema ->
      this.validate(instance[property], propertySchema, path)
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance is present. Returns the validation error message, if any.

  private validateRequired = { instance, schema, path ->
    if (schema.required && instance == null) {
      "groovyschema.required.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance matches the regular expression. Returns the validation error
  // message, if any.

  private validatePattern = { instance, schema, path ->
    if (!(instance instanceof String)) return

    if (!(instance ==~ schema.pattern)) {
      "groovyschema.pattern.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance matches the regular expression referenced by the `format`
  // attribute. Returns the validation error message, if any.

  private validateFormat = { instance, schema, path ->
    if (!(instance instanceof String)) return

    def format = this.formats[schema.format]
    if (format) {
      if (!(instance ==~ format)) {
        "groovyschema.format.message"
      }
    } else {
      throw new IllegalArgumentException("Unknown format '${schema.format}'")
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no lesser than the `minItems` attribute value.
  // Takes into account the `exclusiveMinimum` schema option. Returns the
  // validation error message, if any.

  private validateMinItems = { instance, schema, path ->
    if (!(instance instanceof List)) return

    if (schema.exclusiveMinimum && instance.size() <= schema.minItems || instance.size() < schema.minItems) {
      "groovyschema.minItems.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no greater than the `maxItems` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any.

  private validateMaxItems = { instance, schema, path ->
    if (!(instance instanceof List)) return

    if (schema.exclusiveMaximum && instance.size() >= schema.maxItems || instance.size() > schema.maxItems) {
      "groovyschema.maxItems.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no lesser than the `minLength` attribute value.
  // Takes into account the `exclusiveMinimum` schema option. Returns the
  // validation error message, if any.

  private validateMinLength = { instance, schema, path ->
    if (!(instance instanceof String)) return

    if (schema.exclusiveMinimum && instance.size() <= schema.minLength || instance.size() < schema.minLength) {
      "groovyschema.minLength.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no greater than the `maxLength` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any.

  private validateMaxLength = { instance, schema, path ->
    if (!(instance instanceof String)) return

    if (schema.exclusiveMaximum && instance.size() >= schema.maxLength || instance.size() > schema.maxLength) {
      "groovyschema.maxLength.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance value is no lesser than the `minimum` attribute value. Takes
  // into account the `exclusiveMinimum` schema option. Returns the validation
  // error message, if any.

  private validateMinimum = { instance, schema, path ->
    if (!(instance instanceof Number)) return

    if (schema.exclusiveMinimum && instance <= schema.minimum || instance < schema.minimum) {
      "groovyschema.minimum.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance value is no greater than the `maximum` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any. 

  private validateMaximum = { instance, schema, path ->
    if (!(instance instanceof Number)) return

    if (schema.exclusiveMaximum && instance >= schema.maximum || instance > schema.maximum) {
      "groovyschema.maximum.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates
  // that the given `instance` is divisible by the schema attribute
  // `divisibleBy`. Returns the validation error message, if any. Throws
  // IllegalArgumentException if the `divisibleBy` attribute is 0.

  private validateDivisibleBy = { instance, schema, path ->
    if (!(instance instanceof Number)) return
    if (schema.divisibleBy == 0) throw new IllegalArgumentException("Validation attribute 'divisibleBy' cannot be zero")

    if (instance % schema.divisibleBy != 0) {
      "groovyschema.divisibleBy.message"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that
  // the given `type` attribute matches the instance's type. Returns the
  // validation error message, if any. Throws IllegalArgumentException if the
  // `type` attribute value isn't supported.

  private validateType = { instance, schema, path ->
    if (schema.type == 'any' || instance == null && schema.type != 'null') return

    def valid = false
    switch (schema.type) {
    case 'string':
      valid = instance instanceof String; break
    case 'number':
      valid = instance instanceof Number; break
    case 'integer':
      valid = instance instanceof Integer; break
    case 'boolean':
      valid = instance instanceof Boolean; break
    case 'array':
      valid = instance instanceof List; break
    case 'null':
      valid = instance == null; break
    case 'any':
      valid = true; break
    case 'object':
      valid = instance instanceof Map; break
    default:
      throw new IllegalArgumentException("Value for validation attribute 'type' is not supported")
    }
    if (! valid) {
      "groovyschema.type.message"
    }
  }

  // When `message` is truthy, builds and returns an error object from the
  // passed-in parameters.

  private mkError(message, instance, schema, path) {
    if (message) {
      [
        path: path,
        instance: instance,
        schema: schema,
        message: message.toString(),
      ]
    }
  }

  // Takes an instance and a list of schemas. Validates the instance against all
  // of them and returns a list of return values.

  private collectValidations(instance, schemas, path) {
    schemas.collect { this.validate(instance, it, path) }
  }

  // Regular expressions used with the `format` validation attribute.

  private final formats = [
    'date-time': /^\d{4}-(?:0[0-9]{1}|1[0-2]{1})-[0-9]{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?Z$/,
    'email': /^(?:[\w\!\#\$\%\&\'\*\+\-\/\=\?\^\`\{\|\}\~]+\.)*[\w\!\#\$\%\&\'\*\+\-\/\=\?\^\`\{\|\}\~]+@(?:(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9\-](?!\.)){0,61}[a-zA-Z0-9]?\.)+[a-zA-Z0-9](?:[a-zA-Z0-9\-](?!$)){0,61}[a-zA-Z0-9]?)|(?:\[(?:(?:[01]?\d{1,2}|2[0-4]\d|25[0-5])\.){3}(?:[01]?\d{1,2}|2[0-4]\d|25[0-5])\]))$/,
    'hostname': /^(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z]|[A-Za-z][A-Za-z0-9\-]*[A-Za-z0-9])$/,
    'ipv4': /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
    'ipv6': /^\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*$/,
    'uri': /^[a-zA-Z][a-zA-Z0-9+-.]*:[^\s]*$/,
  ]

  // Filter function that excludes irrelevant properties when scanning for
  // validation properties.

  private final relevantProperties = { key, val ->
    !(key in ['exclusiveMinimum', 'exclusiveMaximum', 'additionalItems'])
  }

  // Takes to parameters and deeply compares them.

  private deepEqual = { a, b ->
    if (a instanceof List) {
      if (!(b instanceof List) || a.size() != b.size()) {
        false
      } else {
        [a, b].transpose().every { deepEqual(it[0], it[1]) }
      }
    } else if (a instanceof Map) {
      if (!(b instanceof Map)) {
        false
      } else {
        def intersection = a.keySet().intersect(b.keySet())
        if (intersection != a.keySet() || intersection != b.keySet()) {
          false
        } else {
          intersection.every { deepEqual(a[it], b[it]) }
        }
      }
    } else {
      a == b
    }
  }

  private metaValidate(schemaInstance, metaSchema, path) {
    def metaValidator = new Validator(isMetaValidating:false)
    def metaErrors = metaValidator.validate(schemaInstance, metaSchema, path)
    if (metaErrors.size()) {
      throw new IllegalArgumentException("schema instance does not comply to meta-schema")
    }
  }

  static final ERRORS_SCHEMA = [
    type: 'array',
    minItems: 0,
    required: true,
    items: [
      type: 'object',
      required: true,
      additionalProperties: false,
      properties: [
        path: [type:'string', required:true],
        instance: [type:'any'], // the validated (sub-)instance e.g. "abc"
        schema: [type:'object', required:true], // the associated (sub-)schema e.g. [format:'email']
        message: [
          type: 'string',
          required: true,
          enum: [
            "groovyschema.additionalItems.message",
            "groovyschema.additionalProperties.message",
            "groovyschema.allOf.message",
            "groovyschema.anyOf.message",
            "groovyschema.dependencies.message",
            "groovyschema.divisibleBy.message",
            "groovyschema.enum.message",
            "groovyschema.fixed.message",
            "groovyschema.format.message",
            "groovyschema.maxItems.message",
            "groovyschema.maxLength.message",
            "groovyschema.maximum.message",
            "groovyschema.minItems.message",
            "groovyschema.minLength.message",
            "groovyschema.minimum.message",
            "groovyschema.not.message",
            "groovyschema.oneOf.message",
            "groovyschema.pattern.message",
            "groovyschema.required.message",
            "groovyschema.type.message",
            "groovyschema.uniqueItems.message"
          ]
        ]
      ]
    ]
  ]

  static final META_SCHEMA = [
    type: 'object',
    required: true,
    additionalProperties: false,
    properties: [

      required: [type:'boolean'],

      type: [type:'string', enum:['string', 'number', 'integer', 'boolean', 'array', 'null', 'any', 'object']],

      enum: [type:'array', minItems:1, items:[type:'any']],

      fixed: [type:'any'],

      pattern: [type:'string'],

      format: [type:'string', enum:['date-time', 'email', 'hostname', 'ipv4', 'ipv6', 'uri']],

      minLength: [type:'number', minimum:0],

      maxLength: [type:'number', minimum:0],

      minimum: [type:'number'],

      maximum: [type:'number'],

      divisibleBy: [type:'number', minimum:0, exclusiveMinimum:true],

      properties: [
        type: 'object',
        patternProperties: [
          /.+/: [type:'object'] // in fact, all values of the `properties` object should comply to this metaschema.
        ]
      ],

      additionalProperties: [
        anyOf: [
          [type:'boolean'],
          [type:'null'],
          [type:'string'],
          [type:'array', items:[type:'string']],
          [type:'object'] // in fact, the schema for all additional properties
        ]
      ],

      patternProperties: [
        type: 'object',
        patternProperties: [
          /.+/: [type:'object'] // in fact, all values of the `patternProperties` object should comply to this metaschema.
        ]
      ],

      dependencies: [
        type: 'object',
        patternProperties: [
          /.+/: [
            anyOf: [
              [type:'string'],
              [type:'array', minItems:1, items:[type:'string']],
              [type:'object'] // in fact, the schema for the dependency
            ]
          ]
        ]
      ],

      items: [
        anyOf: [
          [type:'object'], // in fact, the schema for all items
          [type:'array', items:[type:'object']], // in fact, schemas for each item in the list
        ]
      ],

      additionalItems: [type:'boolean'],

      exclusiveMinimum: [type:'boolean'],

      exclusiveMaximum: [type:'boolean'],

      minItems: [type:'number', minimum:0],

      maxItems: [type:'number', minimum:0],

      uniqueItems: [type:'boolean'],

      allOf: [type:'array', items:[type:'object']], // in fact, an array of schemas

      anyOf: [type:'array', items:[type:'object']], // in fact, an array of schemas

      oneOf: [type:'array', items:[type:'object']], // in fact, an array of schemas

      not: [type:'array', items:[type:'object']], // in fact, an array of schemas
    ]
  ]

}
