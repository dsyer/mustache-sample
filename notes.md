# Migration Notes

This project started out as a blog based on Spring Boot 1.4, abd Spring MVC with [JMustache](https://github.com/samskivert/jmustache). It was trivial to migrate it to Spring Boot 2, and it made it to 2.7.7 without very many changes. When I decided to migrate to Spring Boot 3 (and Spring Security 6), I also wanted to try it with WebFlux, and with reflection-free templates ([JStachio](https://github.com/jstachio/jstachio)). So we have a 2x2 matrix of enhancements, and each one is going to be assigned a label in [github](https://github.com/dsyer/mustache-sample):

|               | MVC            | WebFlux         |
| ------------- | -------------- | --------------- |
| **JMustache** | main           | webflux         |
| **JStachio**  | jstache-webmvc | jstache-webflux |

## Initial Spring Boot Upgrade

Upgrading Spring Boot to 3.0 was trivial. The biggest change wasn't even mandatory: I abandoned the custom `@PostMapping` for `/login` authentication and went with a more connventional `formLogin()` configuration. The IDE recommended that I use a lambda for that, but it would have worked without. So the "main" branch is very similar to the original blog. The other main difference was the addition of a custom error page via an `error.mustache` template that uses the layout and styling from the main application - the kind of thing that people expect real applications to have. It wasn't hard to implement, and Spring Boot detects it immediately because the existing `ViewResolver` resolves "error" to that `View` as described in the [Spring Boot User Guide](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#web.servlet.spring-mvc.error-handling).

## WebFlux and JMustache

Sticking with JMustache but migrating to WebFlux should be easy, I thought. Not quite.

### Default Error Page

The user guide isn't very helpful here. There is a section on [Custom Error Pages](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#web.servlet.spring-mvc.error-handling) but it only mentions resolving specific error codes `error/404` or series `error/5xx`. The default error page turns out to be "error/error" (not "error" like in MVC) so you have to move the `error.mustache` down a level to make it work. ([Pull Request](https://github.com/spring-projects/spring-boot/pull/34534)). It also meant that we had to add `"/error/**"` to the list of unauthenticated paths in the Spring Security configuration.

### Model Attributes Dependencies

(This issue also surfaced in the [PetClinic](https://github.com/spring-petclinic/spring-petclinic-mustache).)

The MVC sample uses `@ControllerAdvice` to populate "global" model attributes that control the layout and menus in the application. With Webflux this turns out to behave differently because the dispatcher doesn't bother trying to work out if there are [dependencies between `@ModelAttribute` methods](https://github.com/spring-projects/spring-framework/issues/20746). So this code:

```java
@ControllerAdvice
class LayoutAdvice {

	@ModelAttribute("layout")
	public Layout layout(Map<String, Object> model) {
		...
	}

	@ModelAttribute("menu")
	public Mustache.Lambda menu(Layout layout) {
		return (frag, out) -> {
			Menu menu = application.getMenu(frag.execute());
			menu.setActive(true);
			layout.title = menu.getTitle();
		};
	}
```

doesn't work (or might not work dependending on which order the methods are called in). We fixed it by removing the `@ModelAttribute` annotations on the dependent methods and replacing them with imperative, explicit method calls. It wasn't hard to implement, but it was very hard to discover the problem because the symptom is runtime exceptions when the menu renders. This fix had to change when we got to the error page rendering anyway (see below).

### Error Page Attributes

WebFlux (unlike MVC) does not use the same dispatcher for error pages as regular HTML views. In particular it does not use a `@Controller`, so the `@ControllerAdvice` that we used in the MVC sample to populate global model state didn't get called, and the error pages could not be rendered. To fix that we had to move the logic in the `@ControllerAdvice` to a helper class and share that with a custom `ErrorAttributes` (a Spring Boot extension point):

```java
@Component
class LayoutErrorAttributes implements ErrorAttributes {

	private final DefaultErrorAttributes delegate = new DefaultErrorAttributes();
	private final LayoutHelper helper;

	public LayoutErrorAttributes(LayoutHelper helper) {
		this.helper = helper;
	}

	...

	@Override
	public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
		Map<String, Object> model = delegate.getErrorAttributes(request, options);
		helper.enhance(request.exchange(), model);
		return model;
	}
}

@ControllerAdvice
class LayoutInterceptor {

	private final LayoutHelper helper;

	public LayoutInterceptor(LayoutHelper helper) {
		this.helper = helper;
	}

	@ModelAttribute
	public void handle(ServerWebExchange request, Map<String, Object> model) {
		helper.enhance(request, model);
	}

}
```

### Spring Security CSRF

The Spring Security User Guide recommends using `@ControllerAdvice` for [including a CSRF token in your model](https://docs.spring.io/spring-security/reference/reactive/exploits/csrf.html#webflux-csrf-include):

```java
@ControllerAdvice
public class SecurityControllerAdvice {
	@ModelAttribute
	Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
		Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
		return csrfToken.doOnSuccess(token -> exchange.getAttributes()
				.put(CsrfRequestDataValueProcessor.DEFAULT_CSRF_ATTR_NAME, token));
	}
}
```

The example takes a request attribute (populated by Spring Security), waits for it to be resolved and then puts it in a different attribute. The name of the model attribute is "monoCsrfToken" which isn't very helpful, and it's still unclear (to me) why this snippet is in the User Guide. It has something to do with the `CsrfRequestDataValueProcessor` which in turn seems to be provided for Thymeleaf, but isn't useful for other template engines.

Reactive model attributes are automatically replaced with their resolved value by the `AbstractView` in Spring WebFlux, so for regular `@Controller` handlers (not error views) this would work:

```java
@ControllerAdvice
public class SecurityControllerAdvice {
	@ModelAttribute("_csrf")
	Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
		return exchange.getAttribute(CsrfToken.class.getName());
	}
}
```

(As far as I know this would work in Thymeleaf too, so why do we need a `CsrfRequestDataValueProcessor` and the more complicated snippet from the user guide?)

Because of the error view not getting a callback for `@ControllerAdvice` this code is in the `LayoutHelper` instead, which is called from 2 different places depending on whether we are in a regular view or an error view:

```java
public void enhance(ServerWebExchange exchange, Map<String, Object> model) {
	...
	if (exchange.getAttribute(CsrfToken.class.getName()) != null) {
		model.put("_csrf", exchange.getAttribute(CsrfToken.class.getName()));
	}
}
```

### Spring Security User Details

You might think that adding a bean of type `UserDetailsService` in a WebFlux application would work the same as in MVC. Actually, if you add one it isn't an error, but Spring Boot ignores it and installs its own default user details - you have to make your custom bean a `ReactiveUserDetailsService`. Maybe Spring Boot could have told me I got that wrong?

## JStachio

JMustache, like pretty much all template engines, needs to reflect on a "context" object (either that or the whole context has to be maps of strings and primitives, which isn't what we came to Java to do). JStachio provides Mustache-based templating without any reflection, and it has some Spring integration out of the box (still quite new and evolving). The point of this migration is to convert from JMustache to JStachio while changing as little as possible otherwise. Note, however that the problems arising are generic issues you will encounter if you go into the weeds with Spring and don't tread the most obvious and most well-used paths. Probably that's a fairly accurate description of a large class of real applications, so lets not focus too much on JStachio.

### MVC Controllers

The design and business logic of the application don't change, but the `@Controller` methods no longer just return a string representing the `View` name. This was a design choice, but it seems to make sense, and JStachio provides a `View` implementation that we can use. For example here is the `/login` page from the MVC app:

```java
@Controller
@RequestMapping("/login")
class LoginController {

	@GetMapping
	public View form() {
		return JStachioModelView.of(new LoginPage());
	}

}
```

The context for the Mustache is a POJO `LoginPage` which knows about the content in the template, and fails at compile time if the template doesn't match the POJO. To support the "global" model attributes for layout all the "page" POJOs in the application extend a base class:

```java
@JStache(path = "login")
class LoginPage extends BasePage {
	public LoginPage() {
		activate("login");
	}
}
```

where `BasePage` has all the global concerns, such as layout, menus, message sources, CRSF token rendering. And the `@JStache` annotation links to the `login.mustache` template (at build time).

### MVC Interceptors

The global concerns are cross-cutting so we don't want the `@Controller` to know about them, and to set them up for each request we need a callback from the dispatcher with all the right context (stuff that is needed in the template rendering): the JStachio context POJO, the MVC model and the current servlet request. The natural way to achieve that is with a `HandlerInterceptor`. JStachio comes with one of those which in turn calls back to user-supplied beans of type `JStachioModelViewConfigurer`. Here's our implementation:

```java

@Component
class ApplicationPageConfigurer implements JStachioModelViewConfigurer {

	private final Application application;

	public ApplicationPageConfigurer(Application application) {
		this.application = application;
	}

	@Override
	public void configure(Object page, Map<String, ?> model, HttpServletRequest request) {
		if (page instanceof BasePage) {
			BasePage base = (BasePage) page;
			base.setCsrfToken((CsrfToken) request.getAttribute("_csrf"));
			Map<String, Object> map = new HashMap<>(model);
			base.setRequestContext(new RequestContext(request, map));
			base.setApplication(application);
		}
		if (page instanceof ErrorPage) {
			((ErrorPage) page).setMessage((String) model.get("error"));
		}
	}

}
```

### Error View

Spring Boot installs a `BeanNameViewResolver` if it finds a bean of type `View`. So we can implement a custom error view using such a bean:

```java
@Component("error")
@RequestScope
class ErrorPageView implements JStachioModelView {

	private ErrorPage page = new ErrorPage();

	@Override
	public Object model() {
		return this.page;
	}

	@Override
	public String getContentType() {
		return "text/html";
	}

}
```

the `ErrorPage` is a `@JStache` model POJO that renders an `error.mustache` template. We use `@RequestScope` because concurrent requests might have different content.

## JStachio WebFlux

This was the most challenging migration. The design is basically the same as the MVC app, but as well as the issues encountered with JMustache we also have to wrangle a few new challenges.

### No Request Scope

The error page can't be request scoped because [there isn't one in WebFlux](https://github.com/spring-projects/spring-framework/issues/28235).

### No BeanNameViewResolver

There also is no `BeanNameViewResolver` (it doesn't exist). We could easily add one, but it wouldn't work without `@RequestScope` so we need another solution anyway. A `ViewResolver` that resolves "error/error" to a new instance of an `ErrorPageView` could work, so that's what we have, but it feels like a bit of a hack. It could cover the global concerns instead of having a custom `ErrorAttributes`.

### No TemplateAvailabilityProvider

Unlike in MVC Spring Boot actually doesn't use the `ViewResolver` to decide if a view exists or not - it asks the `TemplateAvailabilityProvider`. So we also have to provide an instance of that, and a `spring.factories` entry to activate it (which doesn't feel right in application code). It's kind of dumb:

```java
class DemoTemplateAvailabilityProvider implements TemplateAvailabilityProvider {

	@Override
	public boolean isTemplateAvailable(String view, Environment environment, ClassLoader classLoader,
			ResourceLoader resourceLoader) {
		if ("error/error".equals(view)) {
			return true;
		};
		return false;
	}

}
```

### Spring Security CSRF

The `LayoutHelper` is a bit like the JMustache one, but it needs the CSRF request attribute to be copied into the JStachio context `BasePage`. We can do that like this, to make it look a bit like the "normal" reflective template case:

```java
public void enhance(BasePage base, ServerWebExchange exchange, Map<String, Object> model) {
	if (exchange.getAttribute(CsrfToken.class.getName()) != null) {
		@SuppressWarnings("unchecked")
		Mono<CsrfToken> token = (Mono<CsrfToken>) exchange.getAttribute(CsrfToken.class.getName());
		model.put("_csrf", token.doOnSuccess(value -> base.setCsrfToken(value)));
	}
	base.setRequestContext(new RequestContext(exchange, model, exchange.getApplicationContext()));
	base.setApplication(application);
}
```

This copies the `CsrfToken` only when a model attribute called "\_csrf" is resolved, which in turn will only happen if the `View` is an `AbstractView`. This isn't wrong, but it's quite hard to reason about what is going on there, and it's quite a fragile connection from the user code to the framework.

### Layout Helper and View Callbacks

Just as with MVC we need a callback so we can use that `LayoutHelper` to enhance the template context before the `View` is rendered. In MVC we had a `HandlerInterceptor` callback, but in WebFlux there is [no such extension point](https://github.com/spring-projects/spring-framework/issues/24035). JStachio provides one by analogy with the MVC case, but to do this it has to wrap the `ViewResolutionResultHandler` in a `BeanPostProcessor` and intercept the dispatcher chain manually. The callback it then provides has access to the `View` (essentially), the model and the `ServerWebExchange`.
