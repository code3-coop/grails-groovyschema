package ca.code3.groovyschema

class GroovySchemaService {

  def validate(instance, schema) {
    def validator = new Validator()
    validator.validate(instance, schema)
  }
}
