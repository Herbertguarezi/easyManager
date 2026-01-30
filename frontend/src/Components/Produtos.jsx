import React from 'react'
import Produto from './Produto'

const Produtos = () => {
    const [produtos, setProdutos] = React.useState([])
    React.useEffect(() => {
    fetch("http://localhost:8080/products")
      .then(res => res.json())
      .then(data => setProdutos(data))
      .catch(err => console.error("Erro ao buscar produtos:", err));
    }, []);

  return (
    <div>{produtos && produtos.map((produto, index) => (
        <Produto key={produto.id} {...produto} />
    )) }</div>
  )
}

export default Produtos