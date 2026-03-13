# NexoPOS Mobile App - Testing Guide

This document provides comprehensive testing guidelines for the NexoPOS Android mobile application.

## Table of Contents

1. [Testing Overview](#testing-overview)
2. [Running Tests](#running-tests)
3. [Unit Tests](#unit-tests)
4. [Instrumented Tests](#instrumented-tests)
5. [Code Coverage](#code-coverage)
6. [CI/CD Pipeline](#cicd-pipeline)
7. [Best Practices](#best-practices)
8. [Troubleshooting](#troubleshooting)

## Testing Overview

The NexoPOS mobile app implements a multi-layered testing strategy:

```
┌─────────────────────────────────────────────────────────────────┐
│                    TESTING PYRAMID                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                    ┌───────────────────┐                         │
│                    │   E2E Tests       │  (Small % - Critical) │
│                    │   (Instrumented)   │                         │
│                    └───────────────────┘                         │
│                                                                 │
│              ┌───────────────────────────┐                       │
│              │   Integration Tests       │  (Medium %)           │
│              │   (Instrumented + Unit)   │                       │
│              └───────────────────────────┘                       │
│                                                                 │
│        ┌───────────────────────────────────────┐                 │
│        │   Unit Tests                          │  (Large %)      │
│        │   (ViewModels, Repositories, Models)  │                 │
│        └───────────────────────────────────────┘                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Test Locations

| Type | Location | Framework |
|------|----------|-----------|
| Unit Tests | `app/src/test/java/` | JUnit 4, MockK, Coroutines Test |
| Instrumented Tests | `app/src/androidTest/java/` | Espresso, Compose Testing |
| Lint Checks | `app/lint.xml` | Android Lint |

## Running Tests

### Run All Unit Tests

```bash
./gradlew testDebugUnitTest
```

### Run a Specific Test Class

```bash
./gradlew testDebugUnitTest --tests "com.nexopos.mobile.feature.auth.AuthViewModelTest"
```

### Run a Specific Test Method

```bash
./gradlew testDebugUnitTest --tests "com.nexopos.mobile.feature.auth.AuthViewModelTest.login with valid credentials updates auth state to Success"
```

### Run All Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

### Run a Specific Instrumented Test Class

```bash
./gradlew connectedAndroidTest --tests "com.nexopos.mobile.feature.auth.LoginScreenTest"
```

### Run Lint Checks

```bash
./gradlew lintDebug
```

### Run All Checks

```bash
./gradlew check
```

## Unit Tests

### ViewModel Tests

Located in `app/src/test/java/com/nexopos/mobile/feature/`

#### AuthViewModelTest

Tests authentication logic including login, logout, and error handling.

```kotlin
@Test
fun `login with valid credentials updates auth state to Success`() = runTest {
    // Given
    val email = "test@email.com"
    val password = "password123"
    coEvery { authRepository.login(email, password) } returns Result.success(mockLoginResponse())

    // When
    viewModel.login(email, password)
    advanceUntilIdle()

    // Then
    assertTrue(viewModel.loginState.value is LoginState.Success)
}
```

**Key Test Scenarios:**
- Valid login credentials
- Invalid login credentials
- Empty email/password validation
- Logout functionality
- Clear error state

#### CartViewModelTest

Tests shopping cart operations including adding, removing, and updating items.

```kotlin
@Test
fun `add item to cart updates cart items`() = runTest {
    // Given
    val product = createMockProduct()

    // When
    viewModel.addProduct(product, 1.0)
    advanceUntilIdle()

    // Then
    assertTrue(viewModel.state.value.items.isNotEmpty())
}
```

**Key Test Scenarios:**
- Add item to cart
- Remove item from cart
- Update quantity
- Quantity increments for existing items
- Cart size limits
- Invalid quantity handling

#### SearchViewModelTest

Tests product search and filtering functionality.

```kotlin
@Test
fun `search with valid query returns filtered results`() = runTest {
    // Given
    val query = "test product"
    coEvery { productRepository.searchByTerm(query) } returns listOf(mockProduct())

    // When
    viewModel.search(query)
    advanceUntilIdle()

    // Then
    assertEquals(1, viewModel.results.size)
}
```

**Key Test Scenarios:**
- Valid search query
- Short query validation
- Empty results handling
- Error handling
- Loading states

### Repository Tests

Located in `app/src/test/java/com/nexopos/mobile/core/repo/`

#### AuthRepositoryTest

Tests authentication repository operations.

```kotlin
@Test
fun `login returns token on success`() = runTest {
    // Given
    coEvery { api.login(any()) } returns mockLoginResponse()

    // When
    val result = repository.login("email", "password")

    // Then
    assertTrue(result.isSuccess)
    assertNotNull(result.getOrNull()?.data?.token)
}
```

**Key Test Scenarios:**
- Successful login
- Failed login
- Token storage
- Logout and token clearing
- User fetching
- Permissions fetching

#### OrderRepositoryTest

Tests order creation and fetching operations.

```kotlin
@Test
fun `createOrder returns order response on success`() = runTest {
    // Given
    val orderRequest = CreateOrderRequest(...)
    coEvery { api.createOrder(any(), any()) } returns mockOrderResponse()

    // When
    val result = repository.createOrder(orderRequest)

    // Then
    assertTrue(result.isSuccess)
    assertEquals("ORD-001", result.getOrNull()?.orderCode)
}
```

**Key Test Scenarios:**
- Create order
- Update order
- Fetch orders
- Payment methods
- Order types

### Data Class Tests

Located in `app/src/test/java/com/nexopos/mobile/feature/salespos/ui/`

#### CartStateTest

Tests cart state calculations and data structures.

```kotlin
@Test
fun `cart total calculates correctly with multiple items`() {
    // Given
    val items = listOf(
        CartItem(key = "1", productId = 1L, name = "Product 1", quantity = 2.0, unitPrice = 100.0),
        CartItem(key = "2", productId = 2L, name = "Product 2", quantity = 1.0, unitPrice = 50.0)
    )

    // When
    val cartState = CartState(items = items)

    // Then
    assertEquals(250.0, cartState.subtotal, 0.01)
}
```

**Key Test Scenarios:**
- Line total calculations
- Subtotal calculations
- Discount calculations
- Tax calculations
- Change calculations
- Empty cart handling

## Instrumented Tests

Located in `app/src/androidTest/java/com/nexopos/mobile/`

### LoginScreenTest

Tests login screen UI interactions.

```kotlin
@Test
fun loginButton_isEnabled_whenValidCredentialsProvided() {
    // Given
    composeTestRule.setContent { LoginScreen(viewModel) }

    // When
    composeTestRule.onNodeWithText("Email")
        .performTextInput("test@email.com")
    composeTestRule.onNodeWithText("Password")
        .performTextInput("password123")

    // Then
    composeTestRule.onNodeWithText("Login").assertIsEnabled()
}
```

### CartScreenTest

Tests cart screen UI interactions.

```kotlin
@Test
fun cartTotal_isCalculatedCorrectly() {
    // Given
    composeTestRule.setContent { CartScreen(viewModel) }

    // Then
    composeTestRule.onNodeWithText("Total:").assertIsDisplayed()
}
```

### NetworkTest

Tests API response model parsing.

```kotlin
@Test
fun loginApi_responseModel_parsesCorrectly() {
    // Given
    val loginResponse = LoginResponse(
        success = true,
        data = LoginResponse.LoginData(
            token = "test_token",
            user = LoginResponse.UserInfo(
                id = 1L,
                name = "Test User",
                email = "test@example.com"
            )
        )
    )

    // Then
    assertEquals("test_token", loginResponse.data?.token)
}
```

## Code Coverage

### Generate Coverage Report

```bash
./gradlew testDebugUnitTestDebugUnitTestCoverage
```

### View Coverage Report

Open `app/build/reports/coverage/debug/index.html` in a browser.

### Coverage Goals

| Component | Minimum Coverage |
|-----------|------------------|
| ViewModels | 80% |
| Repositories | 85% |
| Data Classes | 90% |
| Overall | 75% |

## CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/android.yml`) runs:

1. **Code Quality Job**
   - Lint checks
   - Code style verification

2. **Unit Tests Job**
   - All unit tests
   - Test result publishing

3. **Build Job**
   - Debug APK compilation
   - Dependency verification

4. **Instrumented Tests Job**
   - UI tests on Android emulator
   - Performance benchmarks

### Running CI Locally

```bash
# Check code quality
./gradlew lint

# Run unit tests
./gradlew test

# Run build verification
./gradlew assembleDebug

# Run all checks
./gradlew check
```

## Best Practices

### Writing Unit Tests

1. **Follow AAA Pattern**
   ```kotlin
   @Test
   fun `test description`() {
       // Arrange
       val input = createInput()
       
       // Act
       val result = systemUnderTest.process(input)
       
       // Assert
       assertEquals(expected, result)
   }
   ```

2. **Use Descriptive Test Names**
   ```kotlin
   // Good
   @Test
   fun `login with invalid credentials shows error message`() { }
   
   // Avoid
   @Test
   fun testLogin() { }
   ```

3. **Test One Thing Per Test**
   ```kotlin
   // Good - single assertion per test
   @Test
   fun `cart total calculates sum of items`() {
       assertEquals(expected, cart.total)
   }
   ```

4. **Mock External Dependencies**
   ```kotlin
   @Before
   fun setup() {
       authRepository = mockk(relaxed = true)
       coEvery { authRepository.login(any(), any()) } returns Result.success(mockResponse())
   }
   ```

5. **Handle Asynchronous Code**
   ```kotlin
   @Test
   fun `login updates state asynchronously`() = runTest {
       viewModel.login(email, password)
       advanceUntilIdle() // Wait for coroutines
       assertTrue(viewModel.loginState.value is LoginState.Success)
   }
   ```

### Writing Instrumented Tests

1. **Use Compose Testing APIs**
   ```kotlin
   composeTestRule.onNodeWithText("Login")
       .assertIsEnabled()
       .performClick()
   ```

2. **Wait for UI Updates**
   ```kotlin
   composeTestRule.waitForIdle()
   ```

3. **Test User Scenarios**
   ```kotlin
   @Test
   fun userCanLoginWithValidCredentials() {
       // Simulate complete user flow
       enterEmail("test@example.com")
       enterPassword("password123")
       clickLogin()
       verifyNavigationToHome()
   }
   ```

## Troubleshooting

### Common Issues

#### 1. Tests Not Running

```bash
# Clean and rebuild
./gradlew clean
./gradlew testDebugUnitTest
```

#### 2. MockK Issues

Ensure dependencies are correctly mocked:
```kotlin
coEvery { repository.method(any()) } returns expectedResult
```

#### 3. Coroutine Tests Hanging

Use `runTest` with `advanceUntilIdle()`:
```kotlin
@Test
fun `async test`() = runTest {
    viewModel.asyncOperation()
    advanceUntilIdle() // Important!
}
```

#### 4. Compose Tests Failing

Ensure proper test setup:
```kotlin
@get:Rule
val composeTestRule = createComposeRule()
```

#### 5. Lint Errors

Run lint with details:
```bash
./gradlew lintDebug --info > lint-output.txt
```

### Getting Help

- Android Testing Documentation: https://developer.android.com/training/testing
- JUnit Documentation: https://junit.org/junit4/
- MockK Documentation: https://mockk.io/
- Compose Testing: https://developer.android.com/jetpack/compose/testing

## Test Configuration

### lint.xml

Custom lint rules are configured in `app/lint.xml`. Key categories:

- **Security**: Hardcoded text, certificate validation
- **Performance**: Overdraw, unused resources
- **Correctness**: New API usage, null safety
- **Accessibility**: Content descriptions, labels

### build.gradle.kts

Test dependencies:
```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("app.cash.turbine:turbine:1.1.0")

androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

## Summary

This testing framework provides:

- ✅ Comprehensive unit test coverage for business logic
- ✅ Instrumented tests for UI verification
- ✅ Automated CI/CD pipeline
- ✅ Code quality enforcement via lint
- ✅ Performance benchmarking capability

Following these guidelines ensures reliable, maintainable code for the NexoPOS mobile application.
