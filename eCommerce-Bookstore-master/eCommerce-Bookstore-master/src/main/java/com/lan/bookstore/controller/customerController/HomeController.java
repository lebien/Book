package com.lan.bookstore.controller.customerController;

import com.lan.bookstore.domain.*;
import com.lan.bookstore.domain.security.PasswordResetToken;
import com.lan.bookstore.domain.security.Role;
import com.lan.bookstore.domain.security.UserRole;
import com.lan.bookstore.service.*;
import com.lan.bookstore.service.impl.UserSecurityService;
import com.lan.bookstore.utility.MailConstructor;
import com.lan.bookstore.utility.SecurityUtility;
import com.lan.bookstore.utility.USConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;
import java.security.Principal;
import java.util.*;

@Controller
public class HomeController {
	
	private static String templatePath = "customer/";
	
	private final JavaMailSender mailSender;

	private final MailConstructor mailConstructor;

	private final UserService userService;

	private final BookService bookService;

	private final OrderService orderService;

	private final CartItemService cartItemService;

	private final UserSecurityService userSecurityService;

	private final UserPaymentService userPaymentService;

	private final UserShippingService userShippingService;

    @Autowired
    public HomeController(JavaMailSender mailSender, MailConstructor mailConstructor, UserService userService, BookService bookService, OrderService orderService, CartItemService cartItemService, UserSecurityService userSecurityService, UserPaymentService userPaymentService, UserShippingService userShippingService) {
        this.mailSender = mailSender;
        this.mailConstructor = mailConstructor;
        this.userService = userService;
        this.bookService = bookService;
        this.orderService = orderService;
        this.cartItemService = cartItemService;
        this.userSecurityService = userSecurityService;
        this.userPaymentService = userPaymentService;
        this.userShippingService = userShippingService;
    }

    /* Main page */
	@RequestMapping("/")
	public String index() {
		return templatePath + "index";
	}

	@RequestMapping("/signUp")
	public String signUp(Model model) {
		model.addAttribute("classActiveNewAccount", true);
		return templatePath + "myAccount";
	}

	@RequestMapping("/myAccount")
	public String myAccount() {
		return templatePath + "myAccount";
	}
	
	@RequestMapping("/hours")
	public String hours(Model model) {
		return templatePath + "hours";
	}
	
	@RequestMapping("/faq")
	public String faq(Model model) {
		return templatePath + "faq";
	}

	@RequestMapping(value = "/newUser", method = RequestMethod.POST)
	public String newUserPost(HttpServletRequest request, @ModelAttribute("email") String userEmail,
                              @ModelAttribute("username") String username, Model model) throws Exception {
		model.addAttribute("classActiveNewAccount", true);
		model.addAttribute("email", userEmail);
		model.addAttribute("username", username);

		if (userService.findByUsername(username) != null) {
			model.addAttribute("userNameExists", true);
			// throw new Exception("Username already exists, nothing will be done");
			return templatePath + "myAccount";
		}

		if (userService.findByEmail(userEmail) != null) {
			model.addAttribute("emailExists", true);
			// throw new Exception("Email already exists, nothin will be done");
			return templatePath + "myAccount";
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(userEmail);

		String password = SecurityUtility.randomPassword();

		String encryptedPassword = SecurityUtility.passwordEncoder().encode(password);
		user.setPassword(encryptedPassword);

		Role role = new Role();
		role.setId((long) 1);
		role.setName("ROLE_USER");
		Set<UserRole> userRoles = new HashSet<>();
		userRoles.add(new UserRole(user, role));
		userService.createUser(user, userRoles);

		String token = UUID.randomUUID().toString();
		userService.createPasswordResetTokenForUser(user, token);

		String appUrl = "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();

		SimpleMailMessage email = mailConstructor.constructResetTokenEmail(appUrl, request.getLocale(), token, user,
				password);

		mailSender.send(email);

		model.addAttribute("emailSent", true);

		return templatePath + "myAccount";
	}

	@RequestMapping("/newUser")
	public String newUser(Locale locale, @RequestParam("token") String token, Model model) {
		PasswordResetToken passToken = userService.getPasswordResetToken(token);

		if (passToken == null) {
			String message = "Invalid Token.";
			model.addAttribute("message", message);
			return "redirect:/badRequest";
		}

		User user = passToken.getUser();
		String username = user.getUsername();

		UserDetails userDetails = userSecurityService.loadUserByUsername(username);

		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
				userDetails.getAuthorities());

		SecurityContextHolder.getContext().setAuthentication(authentication);

		model.addAttribute("user", user);

		model.addAttribute("classActiveEdit", true);
		return templatePath + "myProfile";
	}

	@RequestMapping(value = "/updateUserInfo", method = RequestMethod.POST)
	public String updateUserInfo(@ModelAttribute("user") User user, @ModelAttribute("newPassword") String newPassword,
                                 Model model) throws Exception {
		User currentUser = userService.findById(user.getId());

		if (currentUser == null) {
			throw new Exception("User not found.");
		}

		model.addAttribute("classActiveEdit", true);
		model.addAttribute("orderList", user.getOrderList());
		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("listOfShippingAddresses", true);

		/* Check email already exists */
		if (userService.findByEmail(user.getEmail()) != null) {
			if (!userService.findByEmail(user.getEmail()).getId().equals(currentUser.getId())) {
				model.addAttribute("emailExists", true);
				return templatePath + "myProfile";
			}
		}

		/* Check username already exists */
		if (userService.findByUsername(user.getUsername()) != null) {
			if (!userService.findByUsername(user.getUsername()).getId().equals(currentUser.getId())) {
				model.addAttribute("usernameExists", true);
				return templatePath + "myProfile";
			}
		}

		/* update password */
		if (newPassword != null && !newPassword.isEmpty() && !(newPassword.length() == 0)) {
			BCryptPasswordEncoder passwordEncoder = SecurityUtility.passwordEncoder();
			String dbPassword = currentUser.getPassword();
			if (passwordEncoder.matches(user.getPassword(), dbPassword)) {
				currentUser.setPassword(passwordEncoder.encode(newPassword));
			} else {
				model.addAttribute("incorrectPassword", true);

				return templatePath + "myProfile";
			}
		}

		currentUser.setFirstname(user.getFirstname());
		currentUser.setLastname(user.getLastname());
		currentUser.setUsername(user.getUsername());

		userService.save(currentUser);

		model.addAttribute("updateSuccess", true);
		model.addAttribute("user", currentUser);

		UserDetails userDetails = userSecurityService.loadUserByUsername(currentUser.getUsername());

		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
				userDetails.getAuthorities());

		SecurityContextHolder.getContext().setAuthentication(authentication);

		return templatePath + "myProfile";
	}

	@RequestMapping("/orderDetail")
	public String orderDetail(@RequestParam("id") Long orderId, Principal principal, Model model) {
		User user = userService.findByUsername(principal.getName());
		Order order = orderService.findOne(orderId);

		if (!order.getUser().getId().equals(user.getId())) {
			return "badRequestPage";
		} else {
			List<CartItem> cartItemList = cartItemService.findByOrder(order);
			model.addAttribute("cartItemList", cartItemList);
			model.addAttribute("user", user);
			model.addAttribute("order", order);

			model.addAttribute("userPaymentList", user.getUserPaymentList());
			model.addAttribute("userShippingList", user.getUserShippingList());
			model.addAttribute("orderList", user.getOrderList());

			UserShipping userShipping = new UserShipping();
			model.addAttribute("userShipping", userShipping);

			List<String> stateList = USConstants.listOfUSStatesCode;
			Collections.sort(stateList);
			model.addAttribute("stateList", stateList);

			model.addAttribute("addNewShippingAddress", true);
			model.addAttribute("classActiveOrders", true);
			model.addAttribute("listOfCreditCards", true);
			model.addAttribute("displayOrderDetail", true);

		}

		return templatePath + "myProfile";
	}

	/* Credit Card */
	@RequestMapping("/listOfCreditCards")
	public String listOfCreditCard(Model model, Principal principal, HttpServletRequest request) {
		User user = userService.findByUsername(principal.getName());
		model.addAttribute("user", user);
		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());
		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveBilling", true);
		model.addAttribute("listOfShippingAddresses", true);

		return templatePath + "myProfile";
	}

	/* Add new credit card */
	@RequestMapping("/addNewCreditCard")
	public String addNewCreditCard(Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		model.addAttribute("user", user);

		model.addAttribute("addNewCreditCard", true);
		model.addAttribute("classActiveBilling", true);
		model.addAttribute("listOfShippingAddresses", true);

		UserBilling userBilling = new UserBilling();
		UserPayment userPayment = new UserPayment();

		model.addAttribute("userBilling", userBilling);
		model.addAttribute("userPayment", userPayment);

		List<String> stateList = USConstants.listOfUSStatesCode;
		Collections.sort(stateList);
		model.addAttribute("stateList", stateList);
		model.addAttribute("user", user);
		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		return templatePath + "myProfile";
	}

	@RequestMapping(value = "/addNewCreditCard", method = RequestMethod.POST)
	public String addNewCreditCardPost(@ModelAttribute("userPayment") UserPayment userPayment,
                                       @ModelAttribute("userBilling") UserBilling userBilling, Principal principal, Model model) {
		User user = userService.findByUsername(principal.getName());
		userService.updateUserBilling(userBilling, userPayment, user);

		model.addAttribute("user", user);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveBilling", true);
		model.addAttribute("listOfShippingAddresses", true);

		return templatePath + "myProfile";
	}

	/* Update credit card info */
	@RequestMapping("/updateCreditCard")
	public String updateCreditCard(@ModelAttribute("id") Long creditCardId, Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		UserPayment userPayment = userPaymentService.findById(creditCardId);

		if (!user.getId().equals(userPayment.getUser().getId())) {
			return "badRequestPage";
		} else {
			model.addAttribute("user", user);
			UserBilling userBilling = userPayment.getUserBilling();
			model.addAttribute("userPayment", userPayment);
			model.addAttribute("userBilling", userBilling);

			List<String> stateList = USConstants.listOfUSStatesCode;
			Collections.sort(stateList);
			model.addAttribute("stateList", stateList);

			model.addAttribute("addNewCreditCard", true);
			model.addAttribute("classActiveBilling", true);
			model.addAttribute("listOfShippingAddresses", true);

			model.addAttribute("userPaymentList", user.getUserPaymentList());
			model.addAttribute("userShippingList", user.getUserShippingList());
			model.addAttribute("orderList", user.getOrderList());
		}

		return templatePath + "myProfile";
	}

	/* Remove credit card */
	@RequestMapping("/removeCreditCard")
	public String removeCreditCard(@ModelAttribute("id") Long creditCardId, Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		UserPayment userPayment = userPaymentService.findById(creditCardId);

		if (!user.getId().equals(userPayment.getUser().getId())) {
			return "badRequestPage";
		} else {
			model.addAttribute("user", user);
			userPaymentService.removeById(creditCardId);

			model.addAttribute("listOfCreditCards", true);
			model.addAttribute("classActiveBilling", true);
			model.addAttribute("listOfShippingAddresses", true);

			model.addAttribute("userPaymentList", user.getUserPaymentList());
			model.addAttribute("userShippingList", user.getUserShippingList());
			model.addAttribute("orderList", user.getOrderList());
		}

		return templatePath + "myProfile";
	}

	/* Set default credit card */
	@RequestMapping(value = "/setDefaultPayment", method = RequestMethod.POST)
	public String setDefaultPayment(@ModelAttribute("defaultUserPaymentId") Long defaultUserPaymentId, Model model,
                                    Principal principal) {
		User user = userService.findByUsername(principal.getName());
		userService.setUserDefaultPayment(defaultUserPaymentId, user);

		model.addAttribute("user", user);
		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveBilling", true);
		model.addAttribute("listOfShippingAddresses", true);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		return templatePath + "myProfile";
	}

	/* Shipping address control */
	@RequestMapping("/listOfShippingAddresses")
	public String listOfShippingAddresses(Model model, Principal principal, HttpServletRequest request) {
		User user = userService.findByUsername(principal.getName());
		model.addAttribute("user", user);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveShipping", true);
		model.addAttribute("listOfShippingAddresses", true);

		return templatePath + "myProfile";
	}

	@RequestMapping("/addNewShippingAddress")
	public String addNewShippingAddress(Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		model.addAttribute("user", user);

		model.addAttribute("addNewShippingAddress", true);
		model.addAttribute("classActiveShipping", true);

		UserShipping userShipping = new UserShipping();

		model.addAttribute("userShipping", userShipping);

		UserBilling userBilling = new UserBilling();
		UserPayment userPayment = new UserPayment();

		model.addAttribute("userBilling", userBilling);
		model.addAttribute("userPayment", userPayment);

		List<String> stateList = USConstants.listOfUSStatesCode;
		Collections.sort(stateList);
		model.addAttribute("stateList", stateList);
		model.addAttribute("user", user);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		return templatePath + "myProfile";
	}

	@RequestMapping(value = "/addNewShippingAddress", method = RequestMethod.POST)
	public String addNewShippingAddress(@ModelAttribute("userShipping") UserShipping userShipping, Principal principal,
                                        Model model) {
		User user = userService.findByUsername(principal.getName());
		userService.updateUserShipping(userShipping, user);

		model.addAttribute("user", user);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveShipping", true);
		model.addAttribute("listOfShippingAddresses", true);

		return templatePath + "myProfile";
	}

	/* Update credit card info */
	@RequestMapping("/updateUserShipping")
	public String updateUserShipping(@ModelAttribute("id") Long shippingAddressId, Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		UserShipping userShipping = userShippingService.findById(shippingAddressId);

		if (!user.getId().equals(userShipping.getUser().getId())) {
			return "badRequestPage";
		} else {
			model.addAttribute("user", user);
			model.addAttribute("userShipping", userShipping);

			List<String> stateList = USConstants.listOfUSStatesCode;
			Collections.sort(stateList);
			model.addAttribute("stateList", stateList);

			model.addAttribute("addNewShippingAddress", true);
			model.addAttribute("classActiveShipping", true);
			model.addAttribute("listOfCreditCards", true);

			model.addAttribute("userPaymentList", user.getUserPaymentList());
			model.addAttribute("userShippingList", user.getUserShippingList());
			model.addAttribute("orderList", user.getOrderList());
		}

		return templatePath + "myProfile";
	}

	/* Set default shipping address */
	@RequestMapping(value = "/setDefaultShippingAddress", method = RequestMethod.POST)
	public String setDefaultShippingAddress(
            @ModelAttribute("defaultUserShippingAddressId") Long defaultUserShippingAddressId, Model model,
            Principal principal) {
		User user = userService.findByUsername(principal.getName());
		userService.setUserDefaultShipping(defaultUserShippingAddressId, user);

		model.addAttribute("user", user);
		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("classActiveShipping", true);
		model.addAttribute("listOfShippingAddresses", true);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		return templatePath + "myProfile";
	}

	/* Remove shipping address */
	@RequestMapping("/removeUserShipping")
	public String removeUserShipping(@ModelAttribute("id") Long userShippingId, Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		UserShipping userShipping = userShippingService.findById(userShippingId);

		if (!user.getId().equals(userShipping.getUser().getId())) {
			return "badRequestPage";
		} else {
			model.addAttribute("user", user);
			userShippingService.removeById(userShippingId);

			model.addAttribute("listOfCreditCards", true);
			model.addAttribute("classActiveShipping", true);
			model.addAttribute("listOfShippingAddresses", true);

			model.addAttribute("userPaymentList", user.getUserPaymentList());
			model.addAttribute("userShippingList", user.getUserShippingList());
			model.addAttribute("orderList", user.getOrderList());
		}

		return templatePath + "myProfile";
	}

	@RequestMapping("/myProfile")
	public String myProfile(Model model, Principal principal) {
		User user = userService.findByUsername(principal.getName());
		model.addAttribute("user", user);

		model.addAttribute("userPaymentList", user.getUserPaymentList());
		model.addAttribute("userShippingList", user.getUserShippingList());
		model.addAttribute("orderList", user.getOrderList());

		UserShipping userShipping = new UserShipping();
		model.addAttribute("userShipping", userShipping);

		model.addAttribute("listOfCreditCards", true);
		model.addAttribute("listOfShippingAddresses", true);

		List<String> stateList = USConstants.listOfUSStatesCode;
		Collections.sort(stateList);
		model.addAttribute("stateList", stateList);
		model.addAttribute("classActiveEdit", true);

		return templatePath + "myProfile";
	}

	@RequestMapping("/login")
	public String login(Model model) {
		model.addAttribute("classActiveLogin", true);
		return templatePath + "myAccount";
	}

	@RequestMapping("/forgetPassword")
	public String forgetPassword(HttpServletRequest request, @ModelAttribute("email") String email, Model model)
			throws Exception {
		model.addAttribute("classActiveForgetPassword", true);

		User user = userService.findByEmail(email);

		if (user == null) {
			model.addAttribute("emailNotExist", true);
			// throw new Exception("Email already exists, nothin will be done");
			return templatePath + "myAccount";
		}

		String password = SecurityUtility.randomPassword();

		String encryptedPassword = SecurityUtility.passwordEncoder().encode(password);
		user.setPassword(encryptedPassword);

		Role role = new Role();
		role.setId((long) 1);
		role.setName("ROLE_USER");
		Set<UserRole> userRoles = new HashSet<>();
		userRoles.add(new UserRole(user, role));
		userService.save(user);

		String token = UUID.randomUUID().toString();
		userService.createPasswordResetTokenForUser(user, token);

		String appUrl = "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();

		SimpleMailMessage newEmail = mailConstructor.constructResetTokenEmail(appUrl, request.getLocale(), token, user,
				password);

		mailSender.send(newEmail);

		model.addAttribute("forgetPasswordEmailSent", true);

		return templatePath + "myAccount";
	}

	@RequestMapping("/bookshelf")
	public String bookShelf(Model model, Principal principal) {
		if (principal != null) {
			String username = principal.getName();
			User user = userService.findByUsername(username);
			model.addAttribute("user", user);
		}

		List<Book> bookList = bookService.findAll();
		model.addAttribute("bookList", bookList);
		model.addAttribute("bookList", bookList);
		model.addAttribute("activeAll", true);

		return templatePath + "bookshelf";
	}

	@RequestMapping("/bookDetail")
	public String bookDetail(@PathParam("id") Long id, Model model, Principal principal) {
		if (principal != null) {
			String username = principal.getName();
			User user = userService.findByUsername(username);
			model.addAttribute("user", user);
		}

		Book book = bookService.findOne(id);

		model.addAttribute("book", book);
		List<Integer> qtyList = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

		model.addAttribute("qtyList", qtyList);
		model.addAttribute("qty", 1);

		return templatePath + "bookDetail";
	}
}