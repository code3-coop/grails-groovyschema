class GroovyschemaGrailsPlugin {
  // the plugin version
  def version = "0.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "2.4 > *"
  // resources that are excluded from plugin packaging
  def pluginExcludes = [
    "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def title = "Groovyschema Grails plugin" // Headline display name of the plugin
  def author = "François-Xavier Guillemette"
  def authorEmail = "francois.x.guillemette@code3.ca"
  def description = "Declaratively validate deserialized JSON objects."

  // URL to the plugin's documentation
  def documentation = "http://grails.org/plugin/groovyschema"

  // License: one of 'APACHE', 'GPL2', 'GPL3'
  def license = 'MIT'

  def organization = [ name: "CODE3 Coopérative de solidarité", url: "http://code3.ca" ]

  // Any additional developers beyond the author specified above.
  // def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

  // Location of the plugin's issue tracker.
  def issueManagement = [ system: "github", url: "http://github.com/code3/grails-groovyschema/issues" ]

  // Online location of the plugin's browseable source code.
  def scm = [ url: "http://github.com/code3/grails-groovyschema" ]

  def doWithWebDescriptor = { xml ->
    // TODO Implement additions to web.xml (optional), this event occurs before
  }

  def doWithSpring = {
    // TODO Implement runtime spring config (optional)
  }

  def doWithDynamicMethods = { ctx ->
    // TODO Implement registering dynamic methods to classes (optional)
  }

  def doWithApplicationContext = { ctx ->
    // TODO Implement post initialization spring config (optional)
  }

  def onChange = { event ->
    // TODO Implement code that is executed when any artefact that this plugin is
    // watching is modified and reloaded. The event contains: event.source,
    // event.application, event.manager, event.ctx, and event.plugin.
  }

  def onConfigChange = { event ->
    // TODO Implement code that is executed when the project configuration changes.
    // The event is the same as for 'onChange'.
  }

  def onShutdown = { event ->
    // TODO Implement code that is executed when the application shuts down (optional)
  }
}
