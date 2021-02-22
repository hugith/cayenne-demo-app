package org.apache.cayenne.reflect;

import java.lang.reflect.Method;

import org.apache.cayenne.reflect.Accessor;
import org.apache.cayenne.reflect.AccessorFactory;
import org.apache.cayenne.reflect.Converter;
import org.apache.cayenne.reflect.ConverterFactory;
import org.apache.cayenne.reflect.PropertyUtils;

/**
 * A property accessor that uses set/get methods following JavaBean naming conventions.
 *
 * @since 1.2
 */
public class NonPrefixedBeanAccessor implements Accessor {

	public static class Factory implements AccessorFactory {
		@Override
		public Accessor createAccessor( Class<?> objectClass, String propertyName, Class<?> propertyType ) {
			return new NonPrefixedBeanAccessor( objectClass, propertyName, propertyType );
		}
	}

	private static final long serialVersionUID = 606253801447018099L;

	protected String propertyName;
	protected Method readMethod;
	protected Method writeMethod;
	protected Object nullValue;

	public NonPrefixedBeanAccessor( Class<?> objectClass, String propertyName, Class<?> propertyType ) {
		this( objectClass, propertyName, propertyType, defaultIsGetterName( propertyName ), defaultGetGetterName( propertyName ), defaultSetterName( propertyName ) );
	}

	public static void register() {
		PropertyUtils.installAccessorFactory( new NonPrefixedBeanAccessor.Factory() );
	}

	public NonPrefixedBeanAccessor( Class<?> objectClass, String propertyName, Class<?> propertyType, String isGetterName, String getGetterName, String setterName ) {
		if( objectClass == null ) {
			throw new IllegalArgumentException( "Null objectClass" );
		}

		if( propertyName == null ) {
			throw new IllegalArgumentException( "Null propertyName" );
		}

		if( propertyName.length() == 0 ) {
			throw new IllegalArgumentException( "Empty propertyName" );
		}

		this.propertyName = propertyName;
		this.nullValue = PropertyUtils.defaultNullValueForType( propertyType );

		Method[] publicMethods = objectClass.getMethods();

		Method getter = null;
		for( Method method : publicMethods ) {
			Class<?> returnType = method.getReturnType();
			// following Java Bean naming conventions, "is" methods are preferred over "get" methods
			if( method.getName().equals( isGetterName ) && returnType.equals( Boolean.TYPE ) && method.getParameterTypes().length == 0 ) {
				getter = method;
				break;
			}
			// Find the method with the most specific return type.
			// This is the same behavior as Class.getMethod(String, Class...) except that
			// Class.getMethod prefers synthetic methods generated for interfaces
			// over methods with more specific return types in a super class.
			if( method.getName().equals( getGetterName ) && method.getParameterTypes().length == 0 ) {
				if( returnType.isPrimitive() ) {
					getter = returnType.equals( Void.TYPE ) ? null : method;
					if( returnType.equals( Boolean.TYPE ) ) {
						// keep looking for the "is" method
						continue;
					}
					else {
						// nothing more specific than a primitive, so stop here
						break;
					}
				}
				if( getter == null || getter.getReturnType().isAssignableFrom( returnType ) ) {
					getter = method;
				}
			}
		}

		if( getter == null ) {
			throw new IllegalArgumentException( "Property '" + propertyName + "' is not readable" );
		}

		this.readMethod = getter;

		// TODO: compare 'propertyType' arg with readMethod.getReturnType()

		for( Method method : publicMethods ) {
			if( !method.getName().equals( setterName ) || !method.getReturnType().equals( Void.TYPE ) ) {
				continue;
			}
			Class<?>[] parameterTypes = method.getParameterTypes();
			if( parameterTypes.length != 1 ) {
				continue;
			}
			if( getter.getReturnType().isAssignableFrom( parameterTypes[0] ) ) {
				this.writeMethod = method;
				break;
			}
		}
	}

	private static String defaultGetGetterName( String propertyName ) {
		return propertyName;
	}

	private static String defaultIsGetterName( String propertyName ) {
		return "is" + Character.toUpperCase( propertyName.charAt( 0 ) ) + propertyName.substring( 1 );
	}

	private static String defaultSetterName( String propertyName ) {
		return "set" + Character.toUpperCase( propertyName.charAt( 0 ) ) + propertyName.substring( 1 );
	}

	@Override
	public String getName() {
		return propertyName;
	}

	/**
	 * @since 3.0
	 */
	@Override
	public Object getValue( Object object ) throws PropertyException {

		try {
			return readMethod.invoke( object, (Object[])null );
		}
		catch( Throwable th ) {
			throw new PropertyException( "Error reading property: " + propertyName, this, object, th );
		}
	}

	/**
	 * @since 3.0
	 */
	@Override
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public void setValue( Object object, Object newValue ) throws PropertyException {

		if( writeMethod == null ) {
			throw new PropertyException( "Property '" + propertyName + "' is not writable", this, object );
		}

		Class type = writeMethod.getParameterTypes()[0];
		Converter<?> converter = ConverterFactory.factory.getConverter( type );
		try {
			newValue = (converter != null) ? converter.convert( newValue, type ) : newValue;

			// this will take care of primitives.
			if( newValue == null ) {
				newValue = this.nullValue;
			}

			writeMethod.invoke( object, newValue );
		}
		catch( Throwable th ) {
			throw new PropertyException( "Error writing property: " + propertyName, this, object, th );
		}
	}
}
