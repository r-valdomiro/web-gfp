package gfp.service;

import gfp.model.Usuario;
import logus.commons.persistence.hibernate.transaction.HibernateTransaction;
import logus.commons.persistence.hibernate.transaction.TransactionClass;

import org.springframework.flex.remoting.RemotingDestination;
import org.springframework.flex.remoting.RemotingInclude;
import org.springframework.stereotype.Service;

@Service
@RemotingDestination
public class UsuarioService extends TransactionClass<UsuarioService> {
	
	public static UsuarioService getInstance() throws Exception {
		return new UsuarioService().getEnhancerInstance();
	}
	
	protected UsuarioService() {
		super();
	}
	
	@RemotingInclude
	public Usuario login(final String login, final String senha)
			throws Exception {
		final Usuario result = Usuario.dao.findByFields("login", login,
				"senha", String.valueOf(senha.hashCode()));
		
		if (result == null) {
			throw new Exception("Usuário e/ou Senha inválidos!");
		}
		
		if (!result.isAtivo()) {
			throw new Exception("Usuário desativado!");
		}
		
		return result;
	}
	
	@RemotingInclude
	@HibernateTransaction
	public void salvarUsuario(final Usuario usuario) throws Exception {
		if (usuario.getId() == null) {
			if (Usuario.dao.findByField("login", usuario.getLogin()) != null) {
				throw new Exception(
						"Nome de usuário não disponível para cadastro!");
			}
			
			if (usuario.getEmail() != null &&
					!usuario.getEmail().isEmpty() &&
					Usuario.dao.findByField("email", usuario.getEmail()) != null) {
				throw new Exception("E-Mail já está em uso!");
			}
			
			usuario.save();
		}
	}
	
}
