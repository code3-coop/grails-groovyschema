package ca.code3.groovyschema

import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(GroovySchemaService)
class GroovySchemaServiceSpec extends Specification {

  def "it calls the Validator"() {
    setup:
    def instance = 1
    def schema = [minimum:2]

    when:
    def errors = service.validate(instance, schema)

    then:
    errors.size() == 1
  }
}
