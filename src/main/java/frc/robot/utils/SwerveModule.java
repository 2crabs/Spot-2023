package frc.robot.utils;

import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.sensors.CANCoderConfiguration;
import com.ctre.phoenix.sensors.SensorInitializationStrategy;
import com.ctre.phoenix.sensors.SensorTimeBase;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;

import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Constants;

public class SwerveModule {
  public final int moduleNumber;

  private final CANSparkMax wheelMotor;
  private final RelativeEncoder wheelEncoder;
  private final SparkMaxPIDController wheelPID;
  private final SimpleMotorFeedforward wheelFeedforward;

  private final CANSparkMax turnMotor;
  private final RelativeEncoder turnEncoder;
  private final SparkMaxPIDController turnPID;
  
  private final CANCoder canCoder;
  private final double canCoderOffsetDegrees;

  private double lastAngle;

  public SwerveModule(int moduleNumber, SwerveModuleConstants constants) {
    this.moduleNumber = moduleNumber;
    
    turnMotor = new CANSparkMax(constants.turnMotorID, MotorType.kBrushless);
    wheelMotor = new CANSparkMax(constants.wheelMotorID, MotorType.kBrushless);
    
    wheelPID = wheelMotor.getPIDController();
    turnPID = turnMotor.getPIDController();

    wheelEncoder = wheelMotor.getEncoder();
    turnEncoder = turnMotor.getEncoder();

    canCoder = new CANCoder(constants.canCoderID);
    canCoderOffsetDegrees = constants.canCoderOffsetDegrees;

    wheelFeedforward = new SimpleMotorFeedforward(Constants.kSwerve.DRIVE_KS, Constants.kSwerve.DRIVE_KV, Constants.kSwerve.DRIVE_KA);

    configureDevices();

    lastAngle = getState().angle.getRadians();
  }

  public void setState(SwerveModuleState state, boolean isOpenLoop) {
    // this does everything in the efficientAngle function
    state = SwerveModuleState.optimize(state, getState().angle);

    if (isOpenLoop) {
      double speed = state.speedMetersPerSecond / Constants.kSwerve.MAX_VELOCITY_METERS_PER_SECOND;
      wheelPID.setReference(speed, CANSparkMax.ControlType.kDutyCycle);
    } else {
      wheelPID.setReference(state.speedMetersPerSecond, CANSparkMax.ControlType.kVelocity, 0, wheelFeedforward.calculate(state.speedMetersPerSecond));
    }

    double angle = state.angle.getRadians();
    //if (Math.abs(state.speedMetersPerSecond) <= Constants.kSwerve.MAX_VELOCITY_METERS_PER_SECOND) {
    //  angle = lastAngle;
    //}

    turnPID.setReference(angle, CANSparkMax.ControlType.kPosition);
    lastAngle = angle;
  }

  public SwerveModuleState getState() {
    double velocity = wheelEncoder.getVelocity();
    Rotation2d angle = new Rotation2d(turnEncoder.getPosition());
    return new SwerveModuleState(velocity, angle);
  }

  public double getCanCoder() {
    return canCoder.getAbsolutePosition();
  }

  public Rotation2d getAngle() {
    return new Rotation2d(turnEncoder.getPosition());
  }

  public SwerveModulePosition getPosition() {
    double distance = wheelEncoder.getPosition();
    Rotation2d rot = new Rotation2d(turnEncoder.getPosition());
    return new SwerveModulePosition(distance, rot);
  }
  
  private void configureDevices() {
    // Drive motor configuration.
    wheelMotor.restoreFactoryDefaults();
    wheelMotor.setInverted(Constants.kSwerve.kDriveMotorReversed);
    wheelMotor.setIdleMode(Constants.kSwerve.kDriveIdleMode);
    wheelMotor.setOpenLoopRampRate(Constants.kSwerve.kOpenLoopRamp);
    wheelMotor.setClosedLoopRampRate(Constants.kSwerve.kClosedLoopRamp);
    wheelMotor.setSmartCurrentLimit(Constants.kSwerve.kDriveCurrentLimit);
 
    wheelPID.setP(Constants.kSwerve.DRIVE_KP);
    wheelPID.setI(Constants.kSwerve.DRIVE_KI);
    wheelPID.setD(Constants.kSwerve.DRIVE_KD);
    wheelPID.setFF(Constants.kSwerve.DRIVE_KF);
 
    wheelEncoder.setPositionConversionFactor(Constants.kSwerve.kDriveRotationsToMeters);
    wheelEncoder.setVelocityConversionFactor(Constants.kSwerve.kDriveRpmToMetersPerSecond);
    wheelEncoder.setPosition(0);

    // Angle motor configuration.
    turnMotor.restoreFactoryDefaults();
    turnMotor.setInverted(Constants.kSwerve.kAngleMotorReversed);
    turnMotor.setIdleMode(Constants.kSwerve.kAngleIdleMode);
    turnMotor.setSmartCurrentLimit(Constants.kSwerve.kAngleCurrentLimit);

    turnPID.setP(Constants.kSwerve.ANGLE_KP);
    turnPID.setI(Constants.kSwerve.ANGLE_KI);
    turnPID.setD(Constants.kSwerve.ANGLE_KD);
    turnPID.setFF(Constants.kSwerve.ANGLE_KF);

    turnPID.setPositionPIDWrappingEnabled(true);
    turnPID.setPositionPIDWrappingMaxInput(2 * Math.PI);
    turnPID.setPositionPIDWrappingMinInput(0);

    // PIDS
    turnPID.setReference(0, ControlType.kPosition);
    wheelPID.setReference(0, ControlType.kDutyCycle);

    // CanCoder configuration.
    CANCoderConfiguration canCoderConfiguration = new CANCoderConfiguration();
    canCoderConfiguration.absoluteSensorRange = AbsoluteSensorRange.Unsigned_0_to_360;
    canCoderConfiguration.sensorDirection = Constants.kSwerve.kCanCoderReversed;
    canCoderConfiguration.initializationStrategy = SensorInitializationStrategy.BootToAbsolutePosition;
    canCoderConfiguration.sensorTimeBase = SensorTimeBase.PerSecond;
    
    canCoder.configFactoryDefault();
    canCoder.configAllSettings(canCoderConfiguration);

    SmartDashboard.putNumber("Start" + moduleNumber, canCoder.getAbsolutePosition());

    turnEncoder.setPositionConversionFactor(Constants.kSwerve.kAngleRotationsToRadians);
    turnEncoder.setVelocityConversionFactor(Constants.kSwerve.kAngleRpmToRadiansPerSecond);
    turnEncoder.setPosition(Math.toRadians(canCoder.getAbsolutePosition()+canCoderOffsetDegrees));
    //turnEncoder.setPosition(0);

    SmartDashboard.putNumber("StartRotations" + moduleNumber, canCoder.getAbsolutePosition());
  }
}