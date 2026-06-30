package com.ai.Resume.analyser.service;


import com.ai.Resume.analyser.jwt.jwtService;
import com.ai.Resume.analyser.mail.mailService;
import com.ai.Resume.analyser.model.*;
import com.ai.Resume.analyser.repository.otpVerifyRepo;
import com.ai.Resume.analyser.repository.usersTableRepo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Date;

@Service
public class securityService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);


    @Autowired
    private jwtService jwt;

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private usersTableRepo usersTableRepository;


    @Autowired
    private mailService mailservice;
    @Autowired
    private otpVerifyRepo otpVerifyRepository;

    public ResponseEntity<?> register(userRegister reg) {

        System.out.println("========== REGISTER API ==========");
        System.out.println("Email: " + reg.getEmail());
        System.out.println("OTP from request: " + reg.getVerifyotp());

        otpVerify verify = otpVerifyRepository.findById(reg.getEmail()).orElse(null);

        System.out.println("OTP object from DB: " + verify);

        if (verify == null) {
            System.out.println("OTP record NOT FOUND in database");
            return new ResponseEntity<>("Unauthorised request", HttpStatus.UNAUTHORIZED);
        }

        System.out.println("OTP in DB: " + verify.getVerifyOtp());

        if (!verify.getVerifyOtp().equals(reg.getVerifyotp())) {
            System.out.println("OTP does not match");
            return new ResponseEntity<>("Invalid OTP", HttpStatus.NOT_ACCEPTABLE);
        }

        if (verify.getVerifyExpiration().before(new Date(System.currentTimeMillis()))) {
            System.out.println("OTP expired");
            return new ResponseEntity<>("OTP expired", HttpStatus.NOT_ACCEPTABLE);
        }

        if(! usersTableRepository.existsById(reg.getEmail())){
            usersTable newUser = usersTable.builder()
                    .username(reg.getUsername())
                    .email(reg.getEmail())
                    .password(passwordEncoder.encode(reg.getPassword()))
                    .previousResults(false)
                    .resetOtp(null)
                    .resetExpiration(null)
                    .build();
            usersTableRepository.save(newUser);
            otpVerifyRepository.deleteById(reg.getEmail());
            return new ResponseEntity<>("Successfully created for "+ newUser.getUsername(),HttpStatus.CREATED);
        }

        else{
            return new ResponseEntity<>("User already exist",HttpStatus.NOT_ACCEPTABLE);
        }

    }

    public ResponseEntity<?> verifyEmail(@Valid verifyEmailOtp verifyEmail) {

        if (!usersTableRepository.existsById(verifyEmail.getEmail())) {
            SecureRandom secure = new SecureRandom();
            String otp = String.valueOf(secure.nextInt(900000) + 100000);
            otpVerify otpverify = new otpVerify(
                    verifyEmail.getEmail(),
                    otp,
                    new Date(System.currentTimeMillis() + 10 * 60 * 1000)
            );

            // Save first so OTP exists even if email has a delay
            otpVerifyRepository.save(otpverify);

            try {
                mailservice.sentVerifyOtp(verifyEmail.getUsername(), verifyEmail.getEmail(), otp);
                return new ResponseEntity<>("OTP sent successfully", HttpStatus.OK);
            } catch (Exception e) {
                // Clean up on failure
                otpVerifyRepository.deleteById(verifyEmail.getEmail());
                System.out.println("Mail error - class: " + e.getClass().getName());
                System.out.println("Mail error - message: " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("Mail error - cause: " + e.getCause().getClass().getName() + " | " + e.getCause().getMessage());
                }
                e.printStackTrace();
                return new ResponseEntity<>("Failed to send OTP. Check your email address.", HttpStatus.SERVICE_UNAVAILABLE);
            }
        } else {
            return new ResponseEntity<>("Email already Registered", HttpStatus.CONFLICT);
        }
    }

    public ResponseEntity<?> login(@Valid userLogin req) {

        try{
            authenticationProvider.authenticate( new UsernamePasswordAuthenticationToken(req.getEmail(),req.getPassword()));
            String token = jwt.generateToken(req.getEmail());
            usersTable user =usersTableRepository.findById(req.getEmail()).orElse(null);
            HttpHeaders headers = new HttpHeaders();
            ResponseCookie cookie = ResponseCookie.from("entrypasstoken", token)
                    .path("/")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("None")
                    .maxAge(20 * 24 * 60 * 60)
                    .build();

            headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
            loginResponse loginRes=new loginResponse(user.getUsername(), user.getPreviousResults());
            return new ResponseEntity<>(loginRes,headers,HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Invalid credentials ",HttpStatus.UNAUTHORIZED);
        }

    }



    public ResponseEntity<?> sentResetOtp(@Valid resetOtp req) {
        usersTable user =usersTableRepository.findById(req.getEmail()).orElse(null);
        if(user == null){
            return new ResponseEntity<>("Invalid Email address",HttpStatus.UNAUTHORIZED);
        }
        try{
            SecureRandom secure = new SecureRandom();
            String otp = String.valueOf(secure.nextInt(900000)+100000);
            user.setResetOtp(otp);
            user.setResetExpiration(new Date(System.currentTimeMillis()+10*60*1000));
            usersTableRepository.save(user);
            mailservice.sentResetOtp(user.getUsername(),req.getEmail(),otp);
            return new ResponseEntity<>("OTP sent successfully",HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("Reset OTP mail error - class: " + e.getClass().getName() + " | message: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>("Couldn't sent OTP",HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public ResponseEntity<?> verifyResetOtp(@Valid resetOtpVerification req) {

        usersTable user = usersTableRepository.findById(req.getEmail()).orElse(null);
        if(user==null){
            return new ResponseEntity<>("Unauthorised request",HttpStatus.UNAUTHORIZED);
        }
        if(! user.getResetOtp().equals(req.getOtp()) ){
            return new ResponseEntity<>("Invalid OTP",HttpStatus.NOT_ACCEPTABLE);
        }
        if(user.getResetExpiration().before(new Date(System.currentTimeMillis()))){
            return new ResponseEntity<>("OTP Expired",HttpStatus.NOT_ACCEPTABLE);
        }
        return new ResponseEntity<>("Verified OTP",HttpStatus.OK);
    }

    public ResponseEntity<?> resetAccountPassword(@Valid resetPasscode req) {
        usersTable user = usersTableRepository.findById(req.getEmail()).orElse(null);
        if(user==null){
            return new ResponseEntity<>("Unauthorised request",HttpStatus.UNAUTHORIZED);
        }
        if(! user.getResetOtp().equals(req.getOtp()) ){
            return new ResponseEntity<>("Invalid OTP",HttpStatus.NOT_ACCEPTABLE);
        }
        if(user.getResetExpiration().before(new Date(System.currentTimeMillis()))){
            return new ResponseEntity<>("OTP Expired",HttpStatus.NOT_ACCEPTABLE);
        }
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setResetOtp(null);
        user.setResetExpiration(null);
        usersTableRepository.save(user);
        return  new ResponseEntity<>("Password changed successfully",HttpStatus.OK);

    }
}