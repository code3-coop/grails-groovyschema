package ca.code3.groovyschema

class Validator {

  // Takes in a `schema` and a potential `instance` of it. Validates the top
  // level constraints.

  def validate(instance, schema) {
    schema.findAll(relevantProperties).collect { key, val ->
      def validator = this["validate${key.capitalize()}"]
      if (validator) {
        def err = validator(instance, schema)
        (err instanceof String) ? mkError(err, instance, schema) : err
      } else {
        throw new IllegalArgumentException("Unknown validation attribute '${key}'")
      }
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `allOf` schemas. The instance is valid only if it
  // conforms to all. Returns the validation error messages, if any.

  private validateAllOf = { instance, schema ->
    def errors = collectValidations(instance, schema.allOf).findAll()

    if (errors.size() > 0) {
      "does not comply to all of the given schemas"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `anyOf` schemas. The instance is valid if it
  // conforms to at least one. Returns the validation error messages, if any.

  private validateAnyOf = { instance, schema ->
    def errors = collectValidations(instance, schema.anyOf).findAll()

    if (errors.size() == schema.anyOf.size()) {
      "does not comply to any of the given schema"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `oneOf` schemas. The instance is valid if it
  // conforms to exactly one. Returns the validation error messages, if any.

  private validateOneOf = { instance, schema ->
    def errors = collectValidations(instance, schema.oneOf).findAll()

    if (schema.oneOf.size() - errors.size() != 1) {
      "does not comply to exactly one of the given schema"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates the
  // instance against all the `not` schemas. The instance is valid only if it
  // *does not* conform to all. Returns the validation error message, if any.

  private validateNot = { instance, schema ->
    def prohibitedSchemas = schema.not instanceof List ? schema.not : [schema.not]
    def errors = collectValidations(instance, prohibitedSchemas).findAll()

    if (errors.size() != prohibitedSchemas.size()) {
      "complies to one or more prohibited schemas"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates
  // dependencies to certain properties. Dependencies are either a list of
  // property names that must be present in the instance or additional schemas
  // to which the passed-in instance must comply to. Returns the validation
  // error message, if any.

  private validateDependencies = { instance, schema ->
    if (!(instance instanceof Map)) return

    schema.dependencies.collect { property, description ->
      def dependency = instance[property]
      if (dependency != null) {
        if (description instanceof String || description instanceof List) {
          if (! description.every { instance[it] != null }) {
            mkError("${property} depends on the presence of ${description}", instance, schema)
          }
        } else {
          this.validate(instance, description)
        }
      }
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // instance is one of the enumerated values. Returns the validation error
  // message, if any.

  private validateEnum = { instance, schema ->
    if (! schema.enum.any { deepEqual(it, instance) }) {
      "is not one of ${schema.enum}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // instance is equal to the `fixed` value. Returns the validation error
  // message, if any.

  private validateFixed = { instance, schema ->
    if (! deepEqual(schema.fixed, instance)) {
      "is not ${schema.fixed}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that items
  // in the instance are unique. Returns the validation error message, if any.

  private validateUniqueItems = { instance, schema ->
    if (!(instance instanceof List)) return
    if (! schema.uniqueItems) return

    def uniqued = instance.unique(false, { a, b -> deepEqual(a, b) ? 0 : 1 })

    if (instance.size() != uniqued.size()) {
      "contains duplicate items"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates items in a
  // List. The `items` property can be a schema that describes all items in the
  // instance or an ordered list of schemas, one for each item in the instance.
  // If `items` is a list of schemas, additional items in the instance are
  // allowed only if the `additionalItems` property is `true`. Returns the
  // validation error message, if any.

  private validateItems = { instance, schema ->
    if (!(instance instanceof List)) return

    def items = instance
    if (schema.items instanceof Map) {
      def itemSchema = schema.items
      items.collect { item -> this.validate(item, itemSchema) }.findAll().flatten()
    } else if (items.size() > schema.items.size() && !schema.additionalItems) {
      "additional items are not allowed"
    } else {
      def schemas = schema.items ?: []
      [schemas, items].transpose().collect {
        def itemSchema = it[0]
        def item = it[1]
        this.validate(item, itemSchema)
      }
    }
  }

  // Takes in a `schema` and a potential `instance` of it. `patternProperties`
  // holds a hash mapping regular expressions to schemas. All of the matching
  // instance properties will be validated. Returns the validation error
  // message, if any.

  private validatePatternProperties = { instance, schema ->
    if (!(instance instanceof Map)) return

    instance.collect { property, propertyValue ->
      schema.patternProperties.collect { pattern, propertySchema ->
        if (property ==~ pattern) {
          this.validate(propertyValue, propertySchema)
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

  private validateAdditionalProperties = { instance, schema ->
    if (!(instance instanceof Map)) return
    if (schema.additionalProperties == true) return

    def givenPatterns = schema.patternProperties?.keySet() ?: []
    def given = instance.keySet().findAll { property -> !givenPatterns.any { pattern -> property ==~ pattern } }

    if (schema.additionalProperties == false || (schema.additionalProperties instanceof List)) {
      def possible = (schema.properties?.keySet() ?: []) + (schema.additionalProperties ?: [])
      def additional = given - possible

      if (additional.size() != 0) {
       "additional properties (${additional}) are not allowed"
      }
    } else {
      def additionalPropertySchema = schema.additionalProperties
      def possible = schema.properties?.keySet() ?: []
      def additional = given - possible

      additional.collect { property ->
        this.validate(instance[property], additionalPropertySchema)
      }.findAll().flatten()
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Loops through
  // given properties and calls `validate` for each passing along its (sub-)
  // schema. Returns the validation error message, if any.

  private validateProperties = { instance, schema ->
    if (!(instance instanceof Map)) return

    schema.properties.collect { property, propertySchema ->
      this.validate(instance[property], propertySchema)
    }.findAll().flatten()
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance is present. Returns the validation error message, if any.

  private validateRequired = { instance, schema ->
    if (schema.required && instance == null) {
      "is required"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance matches the regular expression. Returns the validation error
  // message, if any.

  private validatePattern = { instance, schema ->
    if (!(instance instanceof String)) return

    if (!(instance ==~ schema.pattern)) {
      "does not match pattern /${schema.pattern}/"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance matches the regular expression referenced by the `format`
  // attribute. Returns the validation error message, if any.

  private validateFormat = { instance, schema ->
    if (!(instance instanceof String)) return

    def format = this.formats[schema.format]
    if (format) {
      if (!(instance ==~ format)) {
        "does not conform to to '${schema.format}' format"
      }
    } else {
      throw new IllegalArgumentException("Unknown format '${schema.format}'")
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no lesser than the `minItems` attribute value.
  // Takes into account the `exclusiveMinimum` schema option. Returns the
  // validation error message, if any.

  private validateMinItems = { instance, schema ->
    if (!(instance instanceof List)) return

    if (schema.exclusiveMinimum && instance.size() <= schema.minItems || instance.size() < schema.minItems) {
      "does not meet minimum length of ${schema.minItems}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no greater than the `maxItems` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any.

  private validateMaxItems = { instance, schema ->
    if (!(instance instanceof List)) return

    if (schema.exclusiveMaximum && instance.size() >= schema.maxItems || instance.size() > schema.maxItems) {
      "exceeds maximum length of ${schema.maxItems}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no lesser than the `minLength` attribute value.
  // Takes into account the `exclusiveMinimum` schema option. Returns the
  // validation error message, if any.

  private validateMinLength = { instance, schema ->
    if (!(instance instanceof String)) return

    if (schema.exclusiveMinimum && instance.size() <= schema.minLength || instance.size() < schema.minLength) {
      "does not meet minimum length of ${schema.minLength}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance length is no greater than the `maxLength` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any.

  private validateMaxLength = { instance, schema ->
    if (!(instance instanceof String)) return

    if (schema.exclusiveMaximum && instance.size() >= schema.maxLength || instance.size() > schema.maxLength) {
      "exceeds maximum length of ${schema.maxLength}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance value is no lesser than the `minimum` attribute value. Takes
  // into account the `exclusiveMinimum` schema option. Returns the validation
  // error message, if any.

  private validateMinimum = { instance, schema ->
    if (!(instance instanceof Number)) return

    if (schema.exclusiveMinimum && instance <= schema.minimum || instance < schema.minimum) {
      "is less than ${schema.minimum}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that the
  // given instance value is no greater than the `maximum` attribute value.
  // Takes into account the `exclusiveMaximum` schema option. Returns the
  // validation error message, if any. 

  private validateMaximum = { instance, schema ->
    if (!(instance instanceof Number)) return

    if (schema.exclusiveMaximum && instance >= schema.maximum || instance > schema.maximum) {
      "is greater than ${schema.maximum}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates
  // that the given `instance` is divisible by the schema attribute
  // `divisibleBy`. Returns the validation error message, if any. Throws
  // IllegalArgumentException if the `divisibleBy` attribute is 0.

  private validateDivisibleBy = { instance, schema ->
    if (!(instance instanceof Number)) return
    if (schema.divisibleBy == 0) throw new IllegalArgumentException("Validation attribute 'divisibleBy' cannot be zero")

    if (instance % schema.divisibleBy != 0) {
      "is not divisible by ${schema.divisibleBy}"
    }
  }

  // Takes in a `schema` and a potential `instance` of it. Validates that
  // the given `type` attribute matches the instance's type. Returns the
  // validation error message, if any. Throws IllegalArgumentException if the
  // `type` attribute value isn't supported.

  private validateType = { instance, schema ->
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
      "is not of type '${schema.type}'"
    }
  }

  // When `message` is truthy, builds and returns an error object from the
  // passed-in parameters.

  private mkError(message, instance, schema) {
    if (message) {
      [
        instance: instance,
        schema: schema,
        message: message,
      ]
    }
  }

  // Takes an instance and a list of schemas. Validates the instance against all
  // of them and returns a list of return values.

  private collectValidations(instance, schemas) {
    schemas.collect { this.validate(instance, it) }
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
}
